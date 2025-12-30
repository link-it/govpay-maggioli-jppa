package it.govpay.maggioli.batch.service;

import java.util.Base64;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import it.govpay.maggioli.batch.config.BatchProperties;
import it.govpay.maggioli.batch.entity.SingoloVersamento;
import it.govpay.maggioli.batch.utils.SendingUtils;
import it.govpay.maggioli.client.ApiClient;
import it.govpay.maggioli.client.api.NotificheApi;
import it.govpay.maggioli.client.model.RichiestaNotificaPagamentoV2Dto;
import it.govpay.maggioli.client.model.RispostaNotificaPagamentoDto;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for interacting with Maggioli JPPA API
 */
@Service
@Slf4j
public class NotificheApiService {

    private final NotificheApi notificheApi;

    public NotificheApiService(RestTemplate notificheApiRestTemplate, BatchProperties batchProperties) {
        ApiClient apiClient = new ApiClient(notificheApiRestTemplate);
        apiClient.setBasePath(batchProperties.getServiceUrl());
        apiClient.setDebugging(batchProperties.isDebugging());
        this.notificheApi = new NotificheApi(apiClient);
    }

    /**
     * Send notifica ricevuto
     */
    public RispostaNotificaPagamentoDto notificaPagamento(String codDominio, Set<SingoloVersamento> singoliVersamenti, byte[] xmlRt) throws RestClientException {
        try {
            log.debug("Chiamata API per l'invio della notifica di pagamento per il dominio {}", codDominio);

            RichiestaNotificaPagamentoV2Dto notificaPagamento = new RichiestaNotificaPagamentoV2Dto();
			notificaPagamento.setDatiAccertamento(SendingUtils.buildDatiAccertamento(singoliVersamenti));
        	notificaPagamento.setIdentificativoDominioEnteCreditore(codDominio);
        	notificaPagamento.setBase64Ricevuta(Base64.getEncoder().encodeToString(xmlRt));
			ResponseEntity<RispostaNotificaPagamentoDto> responseEntity = notificheApi.postPagamentiV2UsingPOSTWithHttpInfo(notificaPagamento);
            RispostaNotificaPagamentoDto res = responseEntity.getBody();
            log.info("Inviata notifica pagamento: {}", res);

            return res;
        } catch (RestClientException e) {
            log.error("Errore nell'invio notifica pagamento per dominio {}: {}", codDominio, e.getMessage());
            log.error(e.getMessage(), e);
            throw e;
        }
    }

}
