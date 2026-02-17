package it.govpay.maggioli.batch.scheduler;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
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

    private final JobLauncher jobLauncher;
    private final Job maggioliJppaNotificationJob;

    public MaggioliJppaBatchScheduler(
        JobLauncher jobLauncher,
        Job maggioliJppaNotificationJob
    ) {
        this.jobLauncher = jobLauncher;
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

            res = jobLauncher.run(maggioliJppaNotificationJob, jobParameters);

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
