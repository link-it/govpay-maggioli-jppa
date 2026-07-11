package it.govpay.maggioli.batch.config;

import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;

/**
 * Dichiarazione esplicita dei bean infrastrutturali per la gestione batch
 * multi-nodo forniti da {@code govpay-common} ({@link JobExecutionHelper} e
 * {@link JobConcurrencyService}).
 * <p>
 * Sono tenuti separati da {@link BatchJobConfiguration} (che inietta
 * {@code PlatformTransactionManager}) per evitare la dipendenza circolare
 * {@code entityManagerFactory -> batchJobConfiguration -> transactionManager ->
 * entityManagerFactory} introdotta da Spring Batch 6 / Spring Boot 4.
 */
@Configuration
public class BatchInfraConfig {

    private final BatchProperties batchProperties;

    public BatchInfraConfig(BatchProperties batchProperties) {
        this.batchProperties = batchProperties;
    }

    @Bean
    public JobConcurrencyService jobConcurrencyService(JobRepository jobRepository) {
        return new JobConcurrencyService(jobRepository, batchProperties.getStaleThresholdMinutes());
    }

    @Bean
    public JobExecutionHelper jobExecutionHelper(JobOperator jobOperator, JobConcurrencyService jobConcurrencyService) {
        return new JobExecutionHelper(jobOperator, jobConcurrencyService,
            batchProperties.getClusterId(), batchProperties.getZoneId());
    }

    /**
     * Task executor per l'elaborazione parallela degli step di batch.
     * <p>
     * Definito qui (config priva di dipendenze su {@code PlatformTransactionManager})
     * e non in {@link BatchJobConfiguration}: in Spring Boot 4 l'
     * {@code entityManagerFactoryBuilder} risolve un {@code ObjectProvider<AsyncTaskExecutor>}
     * per il bootstrap, e trovarlo dentro {@code BatchJobConfiguration} innescherebbe
     * la dipendenza circolare con l'{@code entityManagerFactory}.
     */
    @Bean
    public SimpleAsyncTaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("maggioli-batch-");
        executor.setConcurrencyLimit(batchProperties.getThreadPoolSize());
        return executor;
    }
}
