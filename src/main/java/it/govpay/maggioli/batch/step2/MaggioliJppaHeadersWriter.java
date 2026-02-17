package it.govpay.maggioli.batch.step2;

import it.govpay.maggioli.batch.dto.MaggioliHeadersBatch;
import it.govpay.maggioli.batch.entity.JppaNotifiche;
import it.govpay.maggioli.batch.repository.JppaNotificheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writer to save JPPA Maggioli receipt info to JPPA_NOTIFICHE table
 */
@Component
@Slf4j
public class MaggioliJppaHeadersWriter implements ItemWriter<MaggioliHeadersBatch> {

    private final JppaNotificheRepository jppaNotificheRepository;

    public MaggioliJppaHeadersWriter(JppaNotificheRepository jppaNotificheRepository) {
        this.jppaNotificheRepository = jppaNotificheRepository;
    }

    @Override
    @Transactional
    public void write(Chunk<? extends MaggioliHeadersBatch> chunk) {
        for (MaggioliHeadersBatch batch : chunk) {
            log.info("Scrittura di {} JPPA Maggioli receipt info per il dominio {}", batch.getHeaders().size(), batch.getCodDominio());

            HeaderProcessingStats stats = new HeaderProcessingStats();

            for (MaggioliHeadersBatch.NotificaHeader header : batch.getHeaders()) {
                processHeader(batch.getCodDominio(), header, stats);
            }

            log.info("Dominio {}: salvati {} nuove JPPA Notifiche, saltati {} già in JPPA_NOTIFICHE",
                     batch.getCodDominio(), stats.savedCount, stats.alreadyInJppaNotificheCount);
        }
    }

    /**
     * Processes a single JPPA Maggioli header and updates statistics.
     * Extracted to avoid multiple continue statements (SonarQube java:S135).
     *
     * @param codDominio the domain code
     * @param header the Maggioli JPPA header to process
     * @param stats statistics object to update
     */
    private void processHeader(String codDominio, MaggioliHeadersBatch.NotificaHeader header, HeaderProcessingStats stats) {
        // verifica: controllare se esiste già in JPPA_NOTIFHICHE
        if (jppaNotificheRepository.existsByIdRptAndCodDominio(header.getIdRpt(), codDominio)) {
            log.debug("JPPA Maggioli {} già presente in JPPA_NOTIFICHE - saltato", header.getIdRpt());
            stats.alreadyInJppaNotificheCount++;
            return;
        }

        // Nuova notifica: inserire in JPPA_NOTIFICHE per elaborazione successiva
        JppaNotifiche notifica = JppaNotifiche.builder()
                                              .idRpt(header.getIdRpt())
                                              .codDominio(codDominio)
                                              .build();
        jppaNotificheRepository.save(notifica);
        stats.savedCount++;
    }

    /**
     * Helper class to track header processing statistics.
     */
    private static class HeaderProcessingStats {
        int savedCount = 0;
        int alreadyInJppaNotificheCount = 0;
    }
}
