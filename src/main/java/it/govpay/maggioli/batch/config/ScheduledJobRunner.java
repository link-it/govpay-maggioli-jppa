package it.govpay.maggioli.batch.config;

import java.time.OffsetDateTime;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.govpay.maggioli.batch.Costanti;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runner per l'esecuzione schedulata del job Maggioli JPPA Notification in modalità multi-nodo.
 * <p>
 * Questa classe gestisce l'esecuzione periodica del batch tramite @Scheduled,
 * implementando la logica di coordinamento tra nodi diversi per evitare esecuzioni
 * concorrenti dello stesso job.
 * <p>
 * Funzionamento:
 * - Prima di avviare il job, verifica se è già in esecuzione (su qualsiasi nodo)
 * - Se è in esecuzione su un altro nodo (clusterId diverso), esce senza avviarlo
 * - Se è in esecuzione sullo stesso nodo, logga un warning (job bloccato)
 * - Se non è in esecuzione, avvia il job passando il clusterId come parametro
 * <p>
 * Attivo solo con profile "default" (non "cron").
 */
@Slf4j
@Component
@Profile("default")
@EnableScheduling
@RequiredArgsConstructor
public class ScheduledJobRunner {

    private final JobLauncher jobLauncher;
    private final PreventConcurrentJobLauncher preventConcurrentJobLauncher;
    @Qualifier("maggioliJppaNotificationJob")
    private final Job maggioliJppaNotificationJob;

    @Value("${govpay.batch.cluster-id:GovPay-JPPA-Batch}")
    private String clusterId;

    /**
     * Esegue il job Maggioli JPPA Notification con i parametri necessari per la gestione multi-nodo.
     *
     * @throws JobExecutionAlreadyRunningException se il job è già in esecuzione
     * @throws JobRestartException se il job non può essere riavviato
     * @throws JobInstanceAlreadyCompleteException se l'istanza del job è già completata
     * @throws JobParametersInvalidException se i parametri del job non sono validi
     */
    private void runMaggioliJppaNotificationJob() throws JobExecutionAlreadyRunningException, JobRestartException,
            JobInstanceAlreadyCompleteException, JobParametersInvalidException {
        JobParameters params = new JobParametersBuilder()
                .addString(Costanti.GOVPAY_BATCH_JOB_ID, Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME)
                .addString(Costanti.GOVPAY_BATCH_JOB_PARAMETER_WHEN, OffsetDateTime.now().toString())
                .addString(Costanti.GOVPAY_BATCH_JOB_PARAMETER_CLUSTER_ID, this.clusterId)
                .toJobParameters();

        jobLauncher.run(maggioliJppaNotificationJob, params);
    }

    /**
     * Metodo schedulato per l'esecuzione periodica del batch Maggioli JPPA Notification.
     * <p>
     * Configurabile tramite properties:
     * - scheduler.maggioliJppaNotificationJob.fixedDelayString: intervallo in millisecondi (default: 10 minuti)
     * - scheduler.initialDelayString: ritardo iniziale in millisecondi (default: 1ms)
     * <p>
     * Prima di avviare il job, verifica se è già in esecuzione su questo nodo o su altri nodi.
     *
     * @throws JobExecutionAlreadyRunningException se il job è già in esecuzione
     * @throws JobRestartException se il job non può essere riavviato
     * @throws JobInstanceAlreadyCompleteException se l'istanza del job è già completata
     * @throws JobParametersInvalidException se i parametri del job non sono validi
     */
    @Scheduled(
        fixedDelayString = "${scheduler.maggioliJppaNotificationJob.fixedDelayString:600000}",
        initialDelayString = "${scheduler.initialDelayString:1}"
    )
    public void runBatchMaggioliJppaNotificationJob() throws JobExecutionAlreadyRunningException, JobRestartException,
            JobInstanceAlreadyCompleteException, JobParametersInvalidException {
        log.info("Esecuzione schedulata di {}", Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME);

        JobExecution currentRunningJobExecution = this.preventConcurrentJobLauncher
                .getCurrentRunningJobExecution(Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME);

        if (currentRunningJobExecution != null) {
            // VERIFICA SE IL JOB È STALE (bloccato o in stato anomalo)
            if (this.preventConcurrentJobLauncher.isJobExecutionStale(currentRunningJobExecution)) {
                log.warn("JobExecution {} rilevata come STALE. Procedo con abbandono e riavvio.",
                    currentRunningJobExecution.getId());

                // Abbandona il job stale
                if (this.preventConcurrentJobLauncher.abandonStaleJobExecution(currentRunningJobExecution)) {
                    log.info("Job stale abbandonato con successo. Avvio nuova esecuzione.");
                    // Procedi con l'avvio di una nuova esecuzione
                    runMaggioliJppaNotificationJob();
                } else {
                    log.error("Impossibile abbandonare il job stale. Uscita senza avviare nuova esecuzione.");
                }
                return;
            }

            // Job in esecuzione normale - estrai il clusterid dell'esecuzione corrente
            String runningClusterId = this.preventConcurrentJobLauncher.getClusterIdFromExecution(currentRunningJobExecution);

            if (runningClusterId != null && !runningClusterId.equals(this.clusterId)) {
                log.info("Il job {} è in esecuzione su un altro nodo ({}). Uscita.",
                    Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME, runningClusterId);
            } else {
                log.warn("Il job {} è ancora in esecuzione sul nodo corrente ({}). Uscita.",
                    Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME, runningClusterId);
            }
            return;
        }

        runMaggioliJppaNotificationJob();
    }
}
