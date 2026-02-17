package it.govpay.maggioli.batch.config;

import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractCronJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.maggioli.batch.Costanti;

@Component
@Profile("cron")
public class CronJobRunner extends AbstractCronJobRunner {

    public CronJobRunner(
            JobExecutionHelper jobExecutionHelper,
            @Qualifier("maggioliJppaNotificationJob") Job maggioliJppaNotificationJob) {
        super(jobExecutionHelper, maggioliJppaNotificationJob, Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME);
    }
}
