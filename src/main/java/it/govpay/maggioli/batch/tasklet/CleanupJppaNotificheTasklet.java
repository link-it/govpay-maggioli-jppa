package it.govpay.maggioli.batch.tasklet;

import it.govpay.maggioli.batch.repository.JppaNotificheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tasklet to clean up JPPA_NOTIFICHE table before starting the batch process
 */
@Component
@Slf4j
public class CleanupJppaNotificheTasklet implements Tasklet {

    private final JppaNotificheRepository jppaNotificheRepository;

    public CleanupJppaNotificheTasklet(JppaNotificheRepository jppaNotificheRepository) {
        this.jppaNotificheRepository = jppaNotificheRepository;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Starting cleanup of JPPA_NOTIFICHE table");

        long count = jppaNotificheRepository.count();
        jppaNotificheRepository.deleteAllRecords();

        log.info("Deleted {} records from JPPA_NOTIFICHE table", count);

        return RepeatStatus.FINISHED;
    }
}
