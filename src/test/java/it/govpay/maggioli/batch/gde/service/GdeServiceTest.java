package it.govpay.maggioli.batch.gde.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.maggioli.batch.gde.mapper.EventoMaggioliMapper;

@ExtendWith(MockitoExtension.class)
class GdeServiceTest {

    @Mock
    private ConfigurazioneService configurazioneService;

    @Mock
    private EventoMaggioliMapper eventoMaggioliMapper;

    @Mock
    private RestTemplate restTemplate;

    private GdeService gdeService;

    private static final String GDE_BASE_URL = "http://gde.example.com/api";
    private static final String COD_DOMINIO = "12345678901";
    private static final String BASE_URL = "http://maggioli.example.com";
    private static final OffsetDateTime DATA_START = OffsetDateTime.of(2025, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DATA_END = OffsetDateTime.of(2025, 1, 1, 10, 0, 5, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        // Use a synchronous executor (Runnable::run) for deterministic async testing
        gdeService = new GdeService(new ObjectMapper(), (Runnable::run), configurazioneService, eventoMaggioliMapper);
    }

    @Test
    @DisplayName("getGdeEndpoint should return servizioGDE url + /eventi")
    void testGetGdeEndpoint() {
        Connettore connettore = new Connettore();
        connettore.setUrl(GDE_BASE_URL);
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);

        String endpoint = gdeService.getGdeEndpoint();

        assertThat(endpoint).isEqualTo(GDE_BASE_URL + "/eventi");
    }

    @Test
    @DisplayName("convertToGdeEvent should throw UnsupportedOperationException")
    void testConvertToGdeEventThrowsUnsupported() {
        GdeEventInfo eventInfo = mock(GdeEventInfo.class);

        assertThatThrownBy(() -> gdeService.convertToGdeEvent(eventInfo))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("sendEventAsync should not call postForEntity when GDE is disabled")
    void testSendEventAsyncDisabled() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(false);
        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("test");

        gdeService.sendEventAsync(evento);

        verify(configurazioneService, never()).getRestTemplateGDE();
    }

    @Test
    @DisplayName("sendEventAsync should call postForEntity when GDE is enabled")
    void testSendEventAsyncSuccess() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);
        Connettore connettore = new Connettore();
        connettore.setUrl(GDE_BASE_URL);
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);

        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("test");

        gdeService.sendEventAsync(evento);

        verify(restTemplate).postForEntity(eq(GDE_BASE_URL + "/eventi"), eq(evento), eq(Void.class));
    }

    @Test
    @DisplayName("sendEventAsync should not propagate exceptions from postForEntity")
    void testSendEventAsyncErrorNotPropagated() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);
        Connettore connettore = new Connettore();
        connettore.setUrl(GDE_BASE_URL);
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenThrow(new RestClientException("GDE unreachable"));

        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("test");

        // Should not throw
        gdeService.sendEventAsync(evento);
    }

    @Test
    @DisplayName("saveLoginOk should create OK event and send it")
    void testSaveLoginOk() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);
        Connettore connettore = new Connettore();
        connettore.setUrl(GDE_BASE_URL);
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);

        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("loginUsingPOST");
        when(eventoMaggioliMapper.createEventoOk(eq(COD_DOMINIO), eq("loginUsingPOST"), any(), eq(DATA_START), eq(DATA_END)))
                .thenReturn(evento);

        ResponseEntity<String> responseEntity = ResponseEntity.ok("token");
        gdeService.saveLoginOk(COD_DOMINIO, DATA_START, DATA_END, responseEntity, BASE_URL);

        verify(eventoMaggioliMapper).createEventoOk(eq(COD_DOMINIO), eq("loginUsingPOST"), any(), eq(DATA_START), eq(DATA_END));
        verify(eventoMaggioliMapper).setParametriRichiesta(eq(evento), any(), eq("POST"), any());
        verify(eventoMaggioliMapper).setParametriRisposta(eq(evento), eq(DATA_END), eq(responseEntity), eq(null));
        verify(restTemplate).postForEntity(eq(GDE_BASE_URL + "/eventi"), eq(evento), eq(Void.class));
    }

    @Test
    @DisplayName("saveLoginKo should create KO event and send it")
    void testSaveLoginKo() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);
        Connettore connettore = new Connettore();
        connettore.setUrl(GDE_BASE_URL);
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);

        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("loginUsingPOST");
        RestClientException exception = new RestClientException("auth failed");
        when(eventoMaggioliMapper.createEventoKo(eq(COD_DOMINIO), eq("loginUsingPOST"), any(),
                eq(DATA_START), eq(DATA_END), any(), eq(exception)))
                .thenReturn(evento);

        gdeService.saveLoginKo(COD_DOMINIO, DATA_START, DATA_END, null, exception, BASE_URL);

        verify(eventoMaggioliMapper).createEventoKo(eq(COD_DOMINIO), eq("loginUsingPOST"), any(),
                eq(DATA_START), eq(DATA_END), eq(null), eq(exception));
        verify(eventoMaggioliMapper).setParametriRisposta(eq(evento), eq(DATA_END), eq(null), eq(exception));
        verify(restTemplate).postForEntity(eq(GDE_BASE_URL + "/eventi"), eq(evento), eq(Void.class));
    }

    @Test
    @DisplayName("saveNotificaPagamentoOk should create OK event and send it")
    void testSaveNotificaPagamentoOk() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);
        Connettore connettore = new Connettore();
        connettore.setUrl(GDE_BASE_URL);
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);

        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("postPagamentiV2UsingPOST");
        when(eventoMaggioliMapper.createEventoOk(eq(COD_DOMINIO), eq("postPagamentiV2UsingPOST"), any(),
                eq(DATA_START), eq(DATA_END)))
                .thenReturn(evento);

        ResponseEntity<String> responseEntity = ResponseEntity.ok("ok");
        gdeService.saveNotificaPagamentoOk(COD_DOMINIO, DATA_START, DATA_END, responseEntity, BASE_URL);

        verify(eventoMaggioliMapper).createEventoOk(eq(COD_DOMINIO), eq("postPagamentiV2UsingPOST"), any(),
                eq(DATA_START), eq(DATA_END));
        verify(restTemplate).postForEntity(eq(GDE_BASE_URL + "/eventi"), eq(evento), eq(Void.class));
    }

    @Test
    @DisplayName("saveNotificaPagamentoKo should create KO event and send it")
    void testSaveNotificaPagamentoKo() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);
        Connettore connettore = new Connettore();
        connettore.setUrl(GDE_BASE_URL);
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);

        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("postPagamentiV2UsingPOST");
        RestClientException exception = new RestClientException("timeout");
        when(eventoMaggioliMapper.createEventoKo(eq(COD_DOMINIO), eq("postPagamentiV2UsingPOST"), any(),
                eq(DATA_START), eq(DATA_END), any(), eq(exception)))
                .thenReturn(evento);

        gdeService.saveNotificaPagamentoKo(COD_DOMINIO, DATA_START, DATA_END, null, exception, BASE_URL);

        verify(eventoMaggioliMapper).createEventoKo(eq(COD_DOMINIO), eq("postPagamentiV2UsingPOST"), any(),
                eq(DATA_START), eq(DATA_END), eq(null), eq(exception));
        verify(restTemplate).postForEntity(eq(GDE_BASE_URL + "/eventi"), eq(evento), eq(Void.class));
    }
}
