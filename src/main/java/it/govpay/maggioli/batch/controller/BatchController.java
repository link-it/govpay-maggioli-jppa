package it.govpay.maggioli.batch.controller;

import java.time.ZoneId;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.common.batch.controller.AbstractBatchController;
import it.govpay.common.batch.dto.BatchStatusInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.maggioli.batch.Costanti;

@RestController
@RequestMapping("/api/batch")
public class BatchController extends AbstractBatchController {

    private final Job maggioliJppaNotificationJob;

    public BatchController(
            JobExecutionHelper jobExecutionHelper,
            JobExplorer jobExplorer,
            @Qualifier("maggioliJppaNotificationJob") Job maggioliJppaNotificationJob,
            Environment environment,
            ZoneId applicationZoneId,
            @Value("${scheduler.maggioliJppaNotificationJob.fixedDelayString:600000}") long schedulerIntervalMillis) {
        super(jobExecutionHelper, jobExplorer, environment, applicationZoneId, schedulerIntervalMillis);
        this.maggioliJppaNotificationJob = maggioliJppaNotificationJob;
    }

    @Override
    protected Job getJob() {
        return maggioliJppaNotificationJob;
    }

    @Override
    protected String getJobName() {
        return Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME;
    }

    @Override
    protected ResponseEntity<String> clearCache() {
        // Nessuna cache applicativa da invalidare
        return ResponseEntity.ok("Nessuna cache da invalidare");
    }

    @GetMapping("/eseguiJob")
    public ResponseEntity<Object> eseguiJobEndpoint(
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        return eseguiJob(force);
    }

    @GetMapping("/status")
    public ResponseEntity<BatchStatusInfo> getStatusEndpoint() {
        return getStatus();
    }

    @GetMapping("/lastExecution")
    public ResponseEntity<LastExecutionInfo> getLastExecutionEndpoint() {
        return getLastExecution();
    }

    @GetMapping("/nextExecution")
    public ResponseEntity<NextExecutionInfo> getNextExecutionEndpoint() {
        return getNextExecution();
    }
}
