package it.govpay.maggioli.batch.gde.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.common.client.gde.HttpDataHolder;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.AbstractGdeService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.common.gde.GdeUtils;
import it.govpay.maggioli.batch.Costanti;
import it.govpay.maggioli.batch.gde.mapper.EventoMaggioliMapper;
import it.govpay.gde.client.beans.NuovoEvento;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GdeService extends AbstractGdeService {

    private final EventoMaggioliMapper eventoMaggioliMapper;
    private final ConfigurazioneService configurazioneService;

    public GdeService(ObjectMapper objectMapper,
                      @Qualifier("asyncHttpExecutor") Executor asyncHttpExecutor,
                      ConfigurazioneService configurazioneService,
                      EventoMaggioliMapper eventoMaggioliMapper) {
        super(objectMapper, asyncHttpExecutor, configurazioneService);
        this.eventoMaggioliMapper = eventoMaggioliMapper;
        this.configurazioneService = configurazioneService;
    }

    @Override
    protected String getGdeEndpoint() {
        return configurazioneService.getServizioGDE().getUrl() + "/eventi";
    }

    @Override
    protected NuovoEvento convertToGdeEvent(GdeEventInfo eventInfo) {
        throw new UnsupportedOperationException(
                "GdeService usa sendEventAsync(NuovoEvento) direttamente, non il pattern GdeEventInfo");
    }

    public void sendEventAsync(NuovoEvento nuovoEvento) {
        if (!isAbilitato()) {
            log.debug("Connettore GDE disabilitato, evento {} non inviato", nuovoEvento.getTipoEvento());
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                getGdeRestTemplate().postForEntity(getGdeEndpoint(), nuovoEvento, Void.class);
                log.debug("Evento {} inviato con successo al GDE", nuovoEvento.getTipoEvento());
            } catch (Exception ex) {
                log.warn("Impossibile inviare evento {} al GDE (il batch continua normalmente): {}",
                        nuovoEvento.getTipoEvento(), ex.getMessage());
                log.debug("Dettaglio errore GDE:", ex);
            } finally {
                HttpDataHolder.clear();
            }
        }, this.asyncExecutor);
    }

    // ==================== Login ====================

    public void saveLoginOk(String codDominio, OffsetDateTime dataStart, OffsetDateTime dataEnd,
                             ResponseEntity<?> responseEntity, String baseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = GdeUtils.buildUrl(baseUrl, Costanti.PATH_LOGIN, null, null);

        NuovoEvento nuovoEvento = eventoMaggioliMapper.createEventoOk(
                codDominio, Costanti.OPERATION_LOGIN, transactionId, dataStart, dataEnd);

        nuovoEvento.setIdDominio(codDominio);

        eventoMaggioliMapper.setParametriRichiesta(nuovoEvento, url, "POST", GdeUtils.getCapturedRequestHeadersAsGdeHeaders());
        eventoMaggioliMapper.setParametriRisposta(nuovoEvento, dataEnd, responseEntity, null);

        setResponsePayload(nuovoEvento, responseEntity, null);

        sendEventAsync(nuovoEvento);
    }

    public void saveLoginKo(String codDominio, OffsetDateTime dataStart, OffsetDateTime dataEnd,
                             ResponseEntity<?> responseEntity, RestClientException exception, String baseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = GdeUtils.buildUrl(baseUrl, Costanti.PATH_LOGIN, null, null);

        NuovoEvento nuovoEvento = eventoMaggioliMapper.createEventoKo(
                codDominio, Costanti.OPERATION_LOGIN, transactionId, dataStart, dataEnd,
                responseEntity, exception);

        nuovoEvento.setIdDominio(codDominio);

        eventoMaggioliMapper.setParametriRichiesta(nuovoEvento, url, "POST", GdeUtils.getCapturedRequestHeadersAsGdeHeaders());
        eventoMaggioliMapper.setParametriRisposta(nuovoEvento, dataEnd, null, exception);

        setResponsePayload(nuovoEvento, responseEntity, exception);

        sendEventAsync(nuovoEvento);
    }

    // ==================== Notifica Pagamento ====================

    public void saveNotificaPagamentoOk(String codDominio, OffsetDateTime dataStart, OffsetDateTime dataEnd,
                                         ResponseEntity<?> responseEntity, String baseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = GdeUtils.buildUrl(baseUrl, Costanti.PATH_NOTIFICA_PAGAMENTO, null, null);

        NuovoEvento nuovoEvento = eventoMaggioliMapper.createEventoOk(
                codDominio, Costanti.OPERATION_NOTIFICA_PAGAMENTO, transactionId, dataStart, dataEnd);

        nuovoEvento.setIdDominio(codDominio);

        eventoMaggioliMapper.setParametriRichiesta(nuovoEvento, url, "POST", GdeUtils.getCapturedRequestHeadersAsGdeHeaders());
        eventoMaggioliMapper.setParametriRisposta(nuovoEvento, dataEnd, responseEntity, null);

        setResponsePayload(nuovoEvento, responseEntity, null);

        sendEventAsync(nuovoEvento);
    }

    public void saveNotificaPagamentoKo(String codDominio, OffsetDateTime dataStart, OffsetDateTime dataEnd,
                                         ResponseEntity<?> responseEntity, RestClientException exception,
                                         String baseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = GdeUtils.buildUrl(baseUrl, Costanti.PATH_NOTIFICA_PAGAMENTO, null, null);

        NuovoEvento nuovoEvento = eventoMaggioliMapper.createEventoKo(
                codDominio, Costanti.OPERATION_NOTIFICA_PAGAMENTO, transactionId, dataStart, dataEnd,
                responseEntity, exception);

        nuovoEvento.setIdDominio(codDominio);

        eventoMaggioliMapper.setParametriRichiesta(nuovoEvento, url, "POST", GdeUtils.getCapturedRequestHeadersAsGdeHeaders());
        eventoMaggioliMapper.setParametriRisposta(nuovoEvento, dataEnd, null, exception);

        setResponsePayload(nuovoEvento, responseEntity, exception);

        sendEventAsync(nuovoEvento);
    }

    // ==================== Utility ====================

    private void setResponsePayload(NuovoEvento nuovoEvento, ResponseEntity<?> responseEntity,
                                     RestClientException exception) {
        if (nuovoEvento.getParametriRisposta() != null) {
            nuovoEvento.getParametriRisposta().setPayload(
                extractResponsePayload(responseEntity, exception));
        }
    }
}
