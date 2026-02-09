package it.govpay.maggioli.batch.service;

import java.util.Base64;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import it.govpay.common.client.service.ConnettoreService;
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

    private final ConnettoreService connettoreService;

    public NotificheApiService(ConnettoreService connettoreService) {
        this.connettoreService = connettoreService;
    }

    /**
     * Send notifica ricevuto
     */
    public RispostaNotificaPagamentoDto notificaPagamento(String codConnettore, String codDominio, Set<SingoloVersamento> singoliVersamenti, byte[] xmlRt) throws RestClientException {
        try {
            log.debug("Chiamata API per l'invio della notifica di pagamento per il dominio {} tramite connettore {}", codDominio, codConnettore);

            ApiClient apiClient = new ApiClient(connettoreService.getRestTemplate(codConnettore));
            apiClient.setBasePath(connettoreService.getConnettore(codConnettore).getUrl());
            NotificheApi notificheApi = new NotificheApi(apiClient);

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
