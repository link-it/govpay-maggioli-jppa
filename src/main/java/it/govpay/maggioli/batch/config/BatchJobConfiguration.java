package it.govpay.maggioli.batch.config;

import it.govpay.maggioli.batch.dto.DominioProcessingContext;
import it.govpay.maggioli.batch.dto.MaggioliHeadersBatch;
import it.govpay.maggioli.batch.entity.RPT;
import it.govpay.maggioli.batch.listener.BatchExecutionRecapListener;
import it.govpay.maggioli.batch.partitioner.DominioPartitioner;
import it.govpay.maggioli.batch.step2.MaggioliJppaHeadersProcessor;
import it.govpay.maggioli.batch.step2.MaggioliJppaHeadersWriter;
import it.govpay.maggioli.batch.step2.MaggioliJppaHeadersReader;
import it.govpay.maggioli.batch.step3.SendNotificationProcessor;
import it.govpay.maggioli.batch.step3.SendNotificationReader;
import it.govpay.maggioli.batch.step3.SendNotificationWriter;
import it.govpay.maggioli.batch.tasklet.CleanupJppaNotificheTasklet;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;
import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClientException;

/**
 * Configuration for Maggioli JPPA Notification Batch Job
 */
@Configuration
@Slf4j
public class BatchJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BatchProperties batchProperties;

    public BatchJobConfiguration(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        BatchProperties batchProperties
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.batchProperties = batchProperties;
    }

	private RetryPolicy retryPolicy() {
		Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(RestClientException.class, true);
        
        // Non ritentare su queste eccezioni
        retryableExceptions.put(IllegalArgumentException.class, false);
        retryableExceptions.put(NullPointerException.class, false);
        
        return new SimpleRetryPolicy(batchProperties.getMaxRetries(), retryableExceptions);
	}

	private BackOffPolicy backOffPolicy() {
		ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000L); // 2 seconds between retries
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000L);
        return backOffPolicy;
	}

    @Bean
    public JobConcurrencyService jobConcurrencyService(JobExplorer jobExplorer) {
        return new JobConcurrencyService(jobExplorer, jobRepository, batchProperties.getStaleThresholdMinutes());
    }

    @Bean
    public JobExecutionHelper jobExecutionHelper(JobLauncher jobLauncher, JobConcurrencyService jobConcurrencyService) {
        return new JobExecutionHelper(jobLauncher, jobConcurrencyService,
            batchProperties.getClusterId(), batchProperties.getZoneId());
    }

    /**
     * Main Maggioli JPPA Notification Job with 2 steps
     */
    @Bean
    public Job maggioliJppaNotificationJob(
        Step cleanupStep,
        Step maggioliHeadersAcquisitionStep,
        Step maggioliSendNotificationStep,
        BatchExecutionRecapListener batchExecutionRecapListener
    ) {
        return new JobBuilder("maggioliJppaNotificationJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .listener(batchExecutionRecapListener)
            .start(cleanupStep)
            .next(maggioliHeadersAcquisitionStep)
            .next(maggioliSendNotificationStep)
            .build();
    }

    /**
     * Step 1: Cleanup JPPA_NOTIFICHE table
     */
    @Bean
    public Step cleanupStep(CleanupJppaNotificheTasklet cleanupJppaNotificheTasklet) {
        return new StepBuilder("cleanupStep", jobRepository)
            .tasklet(cleanupJppaNotificheTasklet, transactionManager)
            .build();
    }

    /**
     * Step 2: Acquire Maggioli JPPA headers (multi-threaded)
     */
    @Bean
    public Step maggioliHeadersAcquisitionStep(
        MaggioliJppaHeadersReader maggioliHeadersReader,
        MaggioliJppaHeadersProcessor maggioliHeadersProcessor,
        MaggioliJppaHeadersWriter maggioliHeadersWriter
    ) {
        return new StepBuilder("maggioliHeadersAcquisitionStep", jobRepository)
            .<DominioProcessingContext, MaggioliHeadersBatch>chunk(batchProperties.getChunkSize(), transactionManager)
            .reader(maggioliHeadersReader)
            .processor(maggioliHeadersProcessor)
            .writer(maggioliHeadersWriter)
            .listener(maggioliHeadersReader) // Register reader as step listener for queue reset
            .taskExecutor(taskExecutor())
            .build();
    }

    @Bean
    public RetryPolicy sendNotificationRetryPolicy() {
    	return retryPolicy();
    }

    @Bean
    public BackOffPolicy sendNotificationBackOffPolicy() {
        return backOffPolicy();
    }

    @Bean
    public RetryListener sendNotificationRetryListener() {
    	return new RetryListener() {
        	@Override
        	public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        		log.info("Retry notification attempt started");
        		return true;
            }
            
            @Override
            public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                log.info("Retry notification attempt closed");
            }
            
            @Override
            public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                log.info(MessageFormat.format("Retry notification attempt #{0} failed: {1}", context.getRetryCount(), throwable.getMessage()));
            }
        };
    }

    /**
     * Step 3: Send Notification to Maggioli JPPA (PARTITIONED by domain)
     */
    @Bean
    public Step maggioliSendNotificationStep(
        DominioPartitioner dominioPartitioner,
        Step maggioliSendNotificationWorkerStep
    ) {
        return new StepBuilder("maggioliSendNotificationStep", jobRepository)
            .partitioner("sendNotificationWorkerStep", dominioPartitioner)
            .step(maggioliSendNotificationWorkerStep)
            .gridSize(batchProperties.getThreadPoolSize()) // Numero di partizioni parallele
            .taskExecutor(taskExecutor())
            .build();
    }

    /**
     * Worker step for Step 3: processes all receipt of a single domain
     */
    @Bean
    public Step maggioliSendNotificationWorkerStep(
        SendNotificationReader sendNotificationReader,
        RetryPolicy sendNotificationRetryPolicy,
        BackOffPolicy sendNotificationBackOffPolicy,
        RetryListener sendNotificationRetryListener,
        SendNotificationProcessor sendNotificationProcessor,
        SendNotificationWriter sendNotificationWriter
    ) {
        return new StepBuilder("sendNotificationWorkerStep", jobRepository)
            .<RPT, SendNotificationProcessor.NotificationCompleteData>chunk(batchProperties.getChunkSize(), transactionManager)
            .reader(sendNotificationReader)
            .processor(sendNotificationProcessor)
            .writer(sendNotificationWriter)
            .listener(sendNotificationWriter) // Register writer as step listener for report and final update
            .faultTolerant()
            .retryPolicy(sendNotificationRetryPolicy)
            .backOffPolicy(sendNotificationBackOffPolicy)
            .retry(RestClientException.class)
            .listener(sendNotificationRetryListener)
            .build();
    }

    /**
     * Task executor for parallel processing in Step 2
     */
    @Bean
    public SimpleAsyncTaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("maggioli-batch-");
        executor.setConcurrencyLimit(batchProperties.getThreadPoolSize());
        return executor;
    }

}
