package it.govpay.maggioli.batch.step2;

import java.util.List;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import it.govpay.maggioli.batch.dto.DominioProcessingContext;
import it.govpay.maggioli.batch.entity.JppaConfig;
import it.govpay.maggioli.batch.repository.JppaConfigRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Reader for enabled domains to fetch JPPA configiuration headers.
 * Thread-safe: uses ConcurrentLinkedQueue to distribute domains across threads.
 */
@Component
@StepScope
@Slf4j
public class MaggioliJppaHeadersReader implements ItemReader<DominioProcessingContext>, StepExecutionListener {

    private final JppaConfigRepository jppaConfigRepository;

    // Thread-safe queue shared across all reader instances within the same step execution
    private static final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.ConcurrentLinkedQueue<JppaConfig>> dominioQueueRef =
        new java.util.concurrent.atomic.AtomicReference<>();
    private static final Object lock = new Object();

    public MaggioliJppaHeadersReader(JppaConfigRepository jppaConfigRepository) {
        this.jppaConfigRepository = jppaConfigRepository;
    }

    @Override
    public DominioProcessingContext read() {
        // Initialize queue once for all threads (thread-safe)
        java.util.concurrent.ConcurrentLinkedQueue<JppaConfig> queue = dominioQueueRef.get();
        if (queue == null) {
            synchronized (lock) {
                queue = dominioQueueRef.get();
                if (queue == null) {
                    List<JppaConfig> jppaConfigInfos = jppaConfigRepository.findAllByAbilitato(Boolean.TRUE);
                    log.info("Trovati {} domini abilitati da processare", jppaConfigInfos.size());
                    queue = new java.util.concurrent.ConcurrentLinkedQueue<>(jppaConfigInfos);
                    dominioQueueRef.set(queue);
                }
            }
        }

        // Each thread polls from the shared queue
        JppaConfig jppaConfigInfos = queue.poll();
        if (jppaConfigInfos != null) {
            log.debug("Lettura dominio: {} (thread: {})", jppaConfigInfos.getCodDominio(), Thread.currentThread().getName());

            return DominioProcessingContext.builder()
                .codDominio(jppaConfigInfos.getCodDominio())
                .codConnettore(jppaConfigInfos.getConnettore())
                .lastRtDate(jppaConfigInfos.getDataUltimaRt())
                .build();
        }

        log.debug("Nessun altro dominio da processare (thread: {})", Thread.currentThread().getName());
        return null; // End of data
    }

    /**
     * Reset queue for next step execution (called by Spring Batch between job executions)
     */
    public static void resetQueue() {
        synchronized (lock) {
            dominioQueueRef.set(null);
        }
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // Reset queue at the beginning of each step execution
        resetQueue();
        log.debug("Coda domini resettata per nuova esecuzione dello step");
    }
}
