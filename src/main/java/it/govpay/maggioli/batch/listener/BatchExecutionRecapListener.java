package it.govpay.maggioli.batch.listener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Listener che stampa un riepilogo dettagliato dell'esecuzione del batch per ogni dominio.
 */
@Component
@Slf4j
public class BatchExecutionRecapListener implements JobExecutionListener {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("=".repeat(80));
        log.info("INIZIO BATCH NOTIFICA MAGGIOLI JPPA");
        log.info("Job ID: {}", jobExecution.getJobId());
        log.info("Avvio: {}", LocalDateTime.now().format(TIME_FORMATTER));
        log.info("=".repeat(80));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("=".repeat(80));
        log.info("RIEPILOGO ESECUZIONE BATCH");
        log.info("=".repeat(80));

        // Statistiche generali
        Duration duration = Duration.between(
            jobExecution.getStartTime(),
            jobExecution.getEndTime()
        );

        log.info("Status finale: {}", jobExecution.getStatus());
        log.info("Durata totale: {} secondi", duration.getSeconds());
        log.info("");

        // Statistiche per step
        printStepStatistics(jobExecution);

        log.info("=".repeat(80));
    }

    private void printStepStatistics(JobExecution jobExecution) {
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();

        // Step: Send Notification
        // TODO
//        stepExecutions.stream()
//            .filter(se -> se.getStepName().equals("maggioliHeadersAcquisitionStep"))
//            .findFirst()
//            .ifPresent(this::printNotificationStats);
    }

    private void printNotificationStats(StepExecution stepExecution) {
        log.info("--- STEP: NOTIFICATION MAGGiOLI JPPA ---");
        log.info("Status: {}", stepExecution.getStatus());
        log.info("Domini processati: {}", stepExecution.getReadCount());
        log.info("Flussi trovati e salvati: {}", stepExecution.getWriteCount());
        log.info("Flussi skippati (già esistenti): {}", stepExecution.getWriteSkipCount());
        log.info("Errori: {}", stepExecution.getReadSkipCount() + stepExecution.getProcessSkipCount());
        long durationMs = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
        log.info("Durata: {} ms", durationMs);
        log.info("");
    }
}
