package it.govpay.maggioli.batch.service;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.maggioli.batch.entity.SingoloVersamento;
import it.govpay.maggioli.batch.gde.service.GdeService;
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
    private final GdeService gdeService;

    public NotificheApiService(ConnettoreService connettoreService, GdeService gdeService) {
        this.connettoreService = connettoreService;
        this.gdeService = gdeService;
    }

    /**
     * Effettua il login sull'API Maggioli e imposta il token Bearer sull'ApiClient.
     */
    private void login(ApiClient apiClient, Connettore connettore, String codDominio, String baseUrl) throws RestClientException {
        JppaLoginRequest loginRequest = new JppaLoginRequest();
        loginRequest.setIdMessaggio(UUID.randomUUID().toString());
        loginRequest.setIdentificativoEnte(codDominio);
        loginRequest.setUsername(connettore.getHttpUser());
        loginRequest.setPassword(connettore.getHttpPassw());

        log.debug("Login API Maggioli per dominio {} con utente {}", codDominio, connettore.getHttpUser());

        AutenticazioneApi autenticazioneApi = new AutenticazioneApi(apiClient);

        OffsetDateTime startLogin = OffsetDateTime.now();
        ResponseEntity<JppaLoginResponse> responseLogin;
        try {
            responseLogin = autenticazioneApi.loginUsingPOSTWithHttpInfo(loginRequest);
            gdeService.saveLoginOk(codDominio, startLogin, OffsetDateTime.now(), responseLogin, baseUrl, loginRequest);
        } catch (RestClientException e) {
            gdeService.saveLoginKo(codDominio, startLogin, OffsetDateTime.now(), null, e, baseUrl, loginRequest);
            throw e;
        }

        JppaLoginResponse loginResponse = responseLogin.getBody();
        if (loginResponse == null || loginResponse.getToken() == null) {
            throw new RestClientException("Login fallito per dominio " + codDominio
                    + ": " + (loginResponse != null ? loginResponse.getDescrizioneErrore() : "risposta vuota"));
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
            RestTemplate restTemplate = connettoreService.getRestTemplate(codConnettore);

            // Rimuove BasicAuthInterceptor: l'autenticazione Maggioli avviene via login JSON + Bearer token
            List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors().stream()
                    .filter(i -> !i.getClass().getSimpleName().contains("BasicAuth")).toList();
            restTemplate.setInterceptors(interceptors);

			ApiClient apiClient = new ApiClient(restTemplate);
            apiClient.setBasePath(connettore.getUrl());
            String baseUrl = connettore.getUrl();

            login(apiClient, connettore, codDominio, baseUrl);

            NotificheApi notificheApi = new NotificheApi(apiClient);

            RichiestaNotificaPagamentoV2Dto notificaPagamento = new RichiestaNotificaPagamentoV2Dto();
			notificaPagamento.setDatiAccertamento(SendingUtils.buildDatiAccertamento(singoliVersamenti));
        	notificaPagamento.setIdentificativoDominioEnteCreditore(codDominio);
        	notificaPagamento.setBase64Ricevuta(Base64.getEncoder().encodeToString(xmlRt));

            OffsetDateTime startNotifica = OffsetDateTime.now();
            ResponseEntity<RispostaNotificaPagamentoDto> responseEntity;
            try {
                responseEntity = notificheApi.postPagamentiV2UsingPOSTWithHttpInfo(notificaPagamento);
                gdeService.saveNotificaPagamentoOk(codDominio, startNotifica, OffsetDateTime.now(), responseEntity, baseUrl, notificaPagamento);
            } catch (RestClientException e) {
                gdeService.saveNotificaPagamentoKo(codDominio, startNotifica, OffsetDateTime.now(), null, e, baseUrl, notificaPagamento);
                throw e;
            }

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
