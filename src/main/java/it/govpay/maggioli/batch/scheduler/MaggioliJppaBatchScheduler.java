package it.govpay.maggioli.batch.scheduler;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler for Maggioli JPPA Notification Batch Job
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "govpay.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MaggioliJppaBatchScheduler {

    private final JobOperator jobOperator;
    private final Job maggioliJppaNotificationJob;

    public MaggioliJppaBatchScheduler(
        JobOperator jobOperator,
        Job maggioliJppaNotificationJob
    ) {
        this.jobOperator = jobOperator;
        this.maggioliJppaNotificationJob = maggioliJppaNotificationJob;
    }

    /**
     * Scheduled execution of Maggioli JPPA Notification Job
     */
    @Scheduled(cron = "${govpay.batch.cron}")
    public JobExecution runMaggioliJppaNotificationJob() {
        log.info("Starting scheduled Maggioli JPPA Notification Job");

        JobExecution res = null;
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            res = jobOperator.start(maggioliJppaNotificationJob, jobParameters);

            log.info("Maggioli JPPA Notification Job completed successfully");

        } catch (Exception e) {
            log.error("Errore nell'esecuzione del Job di Notifiche ricevute a Maggioli JPPA: {}", e.getMessage(), e);
        }
        return res;
    }

    /**
     * Manual trigger for testing
     */
    public void triggerManually() {
        log.info("Manually triggering Maggioli JPPA Notification Job");
        runMaggioliJppaNotificationJob();
    }
}
