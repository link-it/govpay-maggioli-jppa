package it.govpay.maggioli.batch.service;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.maggioli.batch.entity.SingoloVersamento;
import it.govpay.maggioli.batch.utils.SendingUtils;
import it.govpay.maggioli.client.ApiClient;
import it.govpay.maggioli.client.api.AutenticazioneApi;
import it.govpay.maggioli.client.api.NotificheApi;
import it.govpay.maggioli.client.model.JppaLoginRequest;
import it.govpay.maggioli.client.model.JppaLoginResponse;
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
     * Effettua il login sull'API Maggioli e imposta il token Bearer sull'ApiClient.
     */
    private void login(ApiClient apiClient, Connettore connettore, String codDominio) throws RestClientException {
        JppaLoginRequest loginRequest = new JppaLoginRequest();
        loginRequest.setIdMessaggio(UUID.randomUUID().toString());
        loginRequest.setIdentificativoEnte(codDominio);
        loginRequest.setUsername(connettore.getHttpUser());
        loginRequest.setPassword(connettore.getHttpPassw());

        log.debug("Login API Maggioli per dominio {} con utente {}", codDominio, connettore.getHttpUser());

        AutenticazioneApi autenticazioneApi = new AutenticazioneApi(apiClient);
        JppaLoginResponse loginResponse = autenticazioneApi.loginUsingPOST(loginRequest);

        if (loginResponse.getToken() == null) {
            throw new RestClientException("Login fallito per dominio " + codDominio
                    + ": " + loginResponse.getDescrizioneErrore());
        }

        log.debug("Login effettuato con successo per dominio {}, esito: {}", codDominio, loginResponse.getEsito());

        apiClient.setApiKeyPrefix("Bearer");
        apiClient.setApiKey(loginResponse.getToken());
    }

    /**
     * Send notifica ricevuto
     */
    public RispostaNotificaPagamentoDto notificaPagamento(String codConnettore, String codDominio, Set<SingoloVersamento> singoliVersamenti, byte[] xmlRt) throws RestClientException {
        try {
            log.debug("Chiamata API per l'invio della notifica di pagamento per il dominio {} tramite connettore {}", codDominio, codConnettore);

            Connettore connettore = connettoreService.getConnettore(codConnettore);
            ApiClient apiClient = new ApiClient(connettoreService.getRestTemplate(codConnettore));
            apiClient.setBasePath(connettore.getUrl());

            login(apiClient, connettore, codDominio);

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
