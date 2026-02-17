package it.govpay.maggioli.batch.listener;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.listener.AbstractBatchExecutionListener;

@Component
public class BatchExecutionRecapListener extends AbstractBatchExecutionListener {

    @Override
    protected String getBatchName() {
        return "NOTIFICA MAGGIOLI JPPA";
    }

    @Override
    protected void printStepStatistics(JobExecution jobExecution) {
        // Step 1: Cleanup
        StepExecution cleanupStep = findStepByName(jobExecution, "cleanupStep");
        if (cleanupStep != null) {
            printSimpleStepStats(cleanupStep, 1, "CLEANUP JPPA_NOTIFICHE");
        }

        // Step 2: Headers Acquisition
        StepExecution headersStep = findStepByName(jobExecution, "maggioliHeadersAcquisitionStep");
        if (headersStep != null) {
            printSimpleStepStats(headersStep, 2, "ACQUISIZIONE HEADERS MAGGIOLI JPPA");
        }

        // Step 3: Send Notification (partitioned)
        printPartitionedStepStats(jobExecution, "maggioliSendNotificationStep",
            "sendNotificationWorkerStep", 3, "INVIO NOTIFICHE MAGGIOLI JPPA");
    }
}
