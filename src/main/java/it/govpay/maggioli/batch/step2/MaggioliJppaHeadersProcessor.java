package it.govpay.maggioli.batch.step2;

import it.govpay.maggioli.batch.Costanti;
import it.govpay.maggioli.batch.dto.DominioProcessingContext;
import it.govpay.maggioli.batch.dto.MaggioliHeadersBatch;
import it.govpay.maggioli.batch.repository.RptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Processor to fetch JPPA Maggioli receipt info for each domain
 */
@Component
@Slf4j
public class MaggioliJppaHeadersProcessor implements ItemProcessor<DominioProcessingContext, MaggioliHeadersBatch> {

	private final RptRepository rptRepository;

	public MaggioliJppaHeadersProcessor(RptRepository rptRepository) {
        this.rptRepository = rptRepository;
    }

    @Override
    public MaggioliHeadersBatch process(DominioProcessingContext context) throws Exception {
        log.info("Processing domain: {} with last receipt date: {}",
            context.getCodDominio(), context.getLastRtDate());

        try {
            // Fetch receipt to be notify for this domain
        	List<Integer> esitiPgamento = List.of(Costanti.RPT_ESITO_PAGAMENTO_ESEGUITO, Costanti.RPT_ESITO_PAGAMENTO_PARZIALMENTE_ESEGUITO);
            List<MaggioliHeadersBatch.NotificaHeader> headers =
                   context.getLastRtDate() != null ? rptRepository.findIdsByCodDominioAndCodEsitoPagamentoInAndDataMsgRicevutaAfter(context.getCodDominio(), esitiPgamento, context.getLastRtDate())
                                                   : rptRepository.findIdsByCodDominioAndCodEsitoPagamentoIn(context.getCodDominio(), esitiPgamento);

            if (headers.isEmpty()) {
                log.info("Nessuna nuova ricevuta trovata per il dominio {}", context.getCodDominio());
                return null; // Skip this domain
            }
            log.info("Found {} receipts for domain {}", headers.size(), context.getCodDominio());

            return MaggioliHeadersBatch.builder()
                .codDominio(context.getCodDominio())
                .headers(headers)
                .build();

        } catch (RestClientException e) {
            log.error("Errore nell'elaborazione del dominio {}: {}", context.getCodDominio(), e.getMessage());
            // Allow retry mechanism to handle this
            throw e;
        }
    }
}
