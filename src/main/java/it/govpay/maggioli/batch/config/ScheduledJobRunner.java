package it.govpay.maggioli.batch.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractScheduledJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.maggioli.batch.Costanti;

@Component
@Profile("default")
@EnableScheduling
public class ScheduledJobRunner extends AbstractScheduledJobRunner {

    public ScheduledJobRunner(
            JobExecutionHelper jobExecutionHelper,
            @Qualifier("maggioliJppaNotificationJob") Job maggioliJppaNotificationJob) {
        super(jobExecutionHelper, maggioliJppaNotificationJob, Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME);
    }

    @Scheduled(
        fixedDelayString = "${scheduler.maggioliJppaNotificationJob.fixedDelayString:600000}",
        initialDelayString = "${scheduler.initialDelayString:1}"
    )
    public JobExecution runBatchMaggioliJppaNotificationJob() throws JobExecutionAlreadyRunningException,
            JobRestartException, JobInstanceAlreadyCompleteException, InvalidJobParametersException {
        return executeScheduledJob();
    }
}
