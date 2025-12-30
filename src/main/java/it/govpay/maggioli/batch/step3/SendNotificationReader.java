package it.govpay.maggioli.batch.step3;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.govpay.maggioli.batch.entity.RPT;
import it.govpay.maggioli.batch.repository.RptRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Reader RPT per info JPPA_NOTIFICHE di una specifica partizione (dominio).
 * Legge TUTTE le ricevute identificate nelle JPPA_NOTIFICHE del dominio assegnato alla partizione.
 */
@Component
@StepScope
@Slf4j
public class SendNotificationReader implements ItemReader<RPT>, ItemStream {

    private final RptRepository rptRepository;

    @Value("#{stepExecutionContext['codDominio']}")
    private String codDominio;

    @Value("#{stepExecutionContext['partitionNumber']}")
    private Integer partitionNumber;

    @Value("#{stepExecutionContext['totalPartitions']}")
    private Integer totalPartitions;

    private Iterator<RPT> rptIterator;
    private boolean initialized = false;

    public SendNotificationReader(RptRepository rptRepository) {
        this.rptRepository = rptRepository;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (!initialized) {
            log.info("Inizializzazione partizione {}/{} per dominio: {}",
                     partitionNumber, totalPartitions, codDominio);

            // Carica TUTTE le ricevute da notificare di questo dominio
            List<RPT> rpt = rptRepository.findByNotificheOrderByDataMsgRicevuta(codDominio);

            log.info("Partizione {} (dominio {}): trovate {} ricevute da processare",
                     partitionNumber, codDominio, rpt.size());

            rptIterator = rpt.iterator();
            initialized = true;
        }
    }

    @Override
    public RPT read() {
        if (rptIterator != null && rptIterator.hasNext()) {
            RPT ricevuta = rptIterator.next();
            log.debug("Lettura ricevuta per dominio {}: (iuv {} , ccp {})", codDominio, ricevuta.getIuv(), ricevuta.getCcp());
            return ricevuta;
        }

        log.info("Partizione {} (dominio {}): completata lettura di tutte le ricevute da notificare", partitionNumber, codDominio);
        return null; // End of partition data
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        // Niente da salvare nello stato
    }

    @Override
    public void close() throws ItemStreamException {
        // Cleanup se necessario
        rptIterator = null;
    }
}
