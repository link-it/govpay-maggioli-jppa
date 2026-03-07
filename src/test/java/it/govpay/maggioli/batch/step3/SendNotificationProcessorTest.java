package it.govpay.maggioli.batch.step3;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import it.govpay.maggioli.batch.Costanti;
import it.govpay.maggioli.batch.entity.RPT;
import it.govpay.maggioli.batch.entity.Versamento;
import it.govpay.maggioli.batch.exception.LoginFailedException;
import it.govpay.maggioli.batch.service.NotificheApiService;
import it.govpay.maggioli.batch.step3.SendNotificationProcessor.NotificationCompleteData;
import it.govpay.maggioli.client.model.RispostaNotificaPagamentoDto;
import it.govpay.maggioli.client.model.RispostaNotificaPagamentoDto.EsitoEnum;

/**
 * Test per SendNotificationProcessor
 */
@DisplayName("SendNotificationProcessor Tests")
class SendNotificationProcessorTest {

    private static final String COD_CONNETTORE = "CONN_TEST";

    @Mock
    private NotificheApiService notificheApiService;

    private SendNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new SendNotificationProcessor(notificheApiService, COD_CONNETTORE);
    }

    private RPT createRPT() {
        return RPT.builder()
                  .id(1L)
                  .versamento(Versamento.builder().id(101L).build())
                  .codDominio("12345678901")
                  .ccp("CCP_TEST")
                  .iuv("IUV_TEST")
                  .stato("RPT_RICEVUTA_NODO")
                  .codEsitoPagamento(Costanti.RPT_ESITO_PAGAMENTO_ESEGUITO)
                  .dataMsgRicevuta(Instant.now())
                  .xmlRt("XML_RT_TEST".getBytes())
                  .build();
    }

    private RispostaNotificaPagamentoDto createRispostaNotificaPagamento() {
    	RispostaNotificaPagamentoDto response = new RispostaNotificaPagamentoDto();
    	response.setIdentificativoUnivocoVersamento("12345678901");
    	response.setEsito(EsitoEnum.OK);
        return response;
    }

    @Test
    @DisplayName("Test successful processing with complete data")
    void testProcessSuccessWithCompleteData() throws Exception {
    	RPT rpt = createRPT();
    	RispostaNotificaPagamentoDto response = createRispostaNotificaPagamento();

        when(notificheApiService.notificaPagamento(anyString(), anyString(), any(), any())).thenReturn(response);

        NotificationCompleteData result = processor.process(rpt);

        assertNotNull(result);
        assertEquals("12345678901", result.getCodDominio());
        assertEquals(rpt.getDataMsgRicevuta(), result.getDataMsgRicevuta());

        verify(notificheApiService).notificaPagamento(COD_CONNETTORE, rpt.getCodDominio(), rpt.getVersamento().getSingoliVersamenti(), rpt.getXmlRt());
    }

    @Test
    @DisplayName("Test processing throws RestClientException on 5xx")
    void testProcessThrowsRestClientException() throws Exception {
        RPT rpt = createRPT();

        when(notificheApiService.notificaPagamento(anyString(), anyString(), any(), any()))
                                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error"));

        assertThrows(RestClientException.class, () -> processor.process(rpt));

        verify(notificheApiService).notificaPagamento(COD_CONNETTORE, rpt.getCodDominio(), rpt.getVersamento().getSingoliVersamenti(), rpt.getXmlRt());
    }

    @Test
    @DisplayName("Test errore 400 Bad Request restituisce DTO con ERRORE_INVIO senza rilanciare")
    void testProcess400BadRequestReturnsErrorDto() throws Exception {
        RPT rpt = createRPT();

        when(notificheApiService.notificaPagamento(anyString(), anyString(), any(), any()))
                                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, null, null));

        NotificationCompleteData result = processor.process(rpt);

        assertNotNull(result);
        assertEquals(rpt.getCodDominio(), result.getCodDominio());
        assertEquals(rpt.getIuv(), result.getIuv());
        assertEquals(rpt.getCcp(), result.getCcp());
        assertEquals(Costanti.ESITO_ERRORE_INVIO, result.getEsito());
        assertNotNull(result.getErrors());

        verify(notificheApiService).notificaPagamento(COD_CONNETTORE, rpt.getCodDominio(), rpt.getVersamento().getSingoliVersamenti(), rpt.getXmlRt());
    }

    @Test
    @DisplayName("Test errore 4xx diverso da 400 rilancia l'eccezione")
    void testProcess4xxOtherThan400Throws() throws Exception {
        RPT rpt = createRPT();

        when(notificheApiService.notificaPagamento(anyString(), anyString(), any(), any()))
                                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        assertThrows(HttpClientErrorException.class, () -> processor.process(rpt));

        verify(notificheApiService).notificaPagamento(COD_CONNETTORE, rpt.getCodDominio(), rpt.getVersamento().getSingoliVersamenti(), rpt.getXmlRt());
    }

    @Test
    @DisplayName("Test LoginFailedException propagates without being caught")
    void testProcessLoginFailedExceptionPropagates() throws Exception {
        RPT rpt = createRPT();

        when(notificheApiService.notificaPagamento(anyString(), anyString(), any(), any()))
                                .thenThrow(new LoginFailedException("Login fallito"));

        assertThrows(LoginFailedException.class, () -> processor.process(rpt));

        verify(notificheApiService).notificaPagamento(COD_CONNETTORE, rpt.getCodDominio(), rpt.getVersamento().getSingoliVersamenti(), rpt.getXmlRt());
    }
}
