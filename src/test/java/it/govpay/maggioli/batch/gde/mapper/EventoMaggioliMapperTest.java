package it.govpay.maggioli.batch.gde.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gde.client.beans.RuoloEvento;

@ExtendWith(MockitoExtension.class)
class EventoMaggioliMapperTest {

    private EventoMaggioliMapper mapper;

    private static final String CLUSTER_ID = "test-cluster";
    private static final String COD_DOMINIO = "12345678901";
    private static final String TIPO_EVENTO = "loginUsingPOST";
    private static final String TRANSACTION_ID = "txn-001";
    private static final OffsetDateTime DATA_START = OffsetDateTime.of(2025, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DATA_END = OffsetDateTime.of(2025, 1, 1, 10, 0, 5, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws Exception {
        mapper = new EventoMaggioliMapper();
        setField(mapper, "clusterId", CLUSTER_ID);
    }

    @Test
    @DisplayName("createEvento should set all base fields correctly")
    void testCreateEvento() {
        NuovoEvento evento = mapper.createEvento(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID, DATA_START, DATA_END);

        assertThat(evento.getIdDominio()).isEqualTo(COD_DOMINIO);
        assertThat(evento.getCategoriaEvento()).isEqualTo(CategoriaEvento.INTERFACCIA);
        assertThat(evento.getClusterId()).isEqualTo(CLUSTER_ID);
        assertThat(evento.getDataEvento()).isEqualTo(DATA_START);
        assertThat(evento.getDurataEvento()).isEqualTo(5L);
        assertThat(evento.getRuolo()).isEqualTo(RuoloEvento.CLIENT);
        assertThat(evento.getComponente()).isEqualTo(ComponenteEvento.API_MAGGIOLI_JPPA);
        assertThat(evento.getTipoEvento()).isEqualTo(TIPO_EVENTO);
        assertThat(evento.getTransactionId()).isEqualTo(TRANSACTION_ID);
    }

    @Test
    @DisplayName("createEventoOk should set esito OK")
    void testCreateEventoOk() {
        NuovoEvento evento = mapper.createEventoOk(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID, DATA_START, DATA_END);

        assertThat(evento.getEsito()).isEqualTo(EsitoEvento.OK);
        assertThat(evento.getComponente()).isEqualTo(ComponenteEvento.API_MAGGIOLI_JPPA);
    }

    @Test
    @DisplayName("createEventoKo with HttpClientErrorException (404) should set esito KO")
    void testCreateEventoKoWithHttpClientErrorException() {
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null);

        NuovoEvento evento = mapper.createEventoKo(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID,
                DATA_START, DATA_END, null, exception);

        assertThat(evento.getEsito()).isEqualTo(EsitoEvento.KO);
        assertThat(evento.getSottotipoEsito()).isEqualTo("404");
    }

    @Test
    @DisplayName("createEventoKo with HttpServerErrorException (500) should set esito FAIL")
    void testCreateEventoKoWithHttpServerErrorException() {
        HttpServerErrorException exception = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", HttpHeaders.EMPTY, null, null);

        NuovoEvento evento = mapper.createEventoKo(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID,
                DATA_START, DATA_END, null, exception);

        assertThat(evento.getEsito()).isEqualTo(EsitoEvento.FAIL);
        assertThat(evento.getSottotipoEsito()).isEqualTo("500");
    }

    @Test
    @DisplayName("createEventoKo with generic RestClientException should set esito FAIL and sottotipo 500")
    void testCreateEventoKoWithGenericRestClientException() {
        RestClientException exception = new RestClientException("Connection refused");

        NuovoEvento evento = mapper.createEventoKo(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID,
                DATA_START, DATA_END, null, exception);

        assertThat(evento.getEsito()).isEqualTo(EsitoEvento.FAIL);
        assertThat(evento.getSottotipoEsito()).isEqualTo("500");
        assertThat(evento.getDettaglioEsito()).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("createEventoKo with responseEntity only (4xx) should set esito KO from status")
    void testCreateEventoKoWithResponseEntityOnly() {
        ResponseEntity<String> responseEntity = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("bad");

        NuovoEvento evento = mapper.createEventoKo(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID,
                DATA_START, DATA_END, responseEntity, null);

        assertThat(evento.getEsito()).isEqualTo(EsitoEvento.KO);
        assertThat(evento.getSottotipoEsito()).isEqualTo("400");
    }

    @Test
    @DisplayName("createEventoKo with responseEntity 5xx should set esito FAIL")
    void testCreateEventoKoWithResponseEntity5xx() {
        ResponseEntity<String> responseEntity = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");

        NuovoEvento evento = mapper.createEventoKo(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID,
                DATA_START, DATA_END, responseEntity, null);

        assertThat(evento.getEsito()).isEqualTo(EsitoEvento.FAIL);
        assertThat(evento.getSottotipoEsito()).isEqualTo("500");
    }

    @Test
    @DisplayName("setParametriRichiesta should set url, method, headers and dataOraRichiesta")
    void testSetParametriRichiesta() {
        NuovoEvento evento = mapper.createEvento(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID, DATA_START, DATA_END);
        Header header = new Header();
        header.setNome("Content-Type");
        header.setValore("application/json");

        mapper.setParametriRichiesta(evento, "http://localhost/api", "POST", List.of(header));

        assertThat(evento.getParametriRichiesta()).isNotNull();
        assertThat(evento.getParametriRichiesta().getUrl()).isEqualTo("http://localhost/api");
        assertThat(evento.getParametriRichiesta().getMethod()).isEqualTo("POST");
        assertThat(evento.getParametriRichiesta().getHeaders()).hasSize(1);
        assertThat(evento.getParametriRichiesta().getDataOraRichiesta()).isEqualTo(DATA_START);
    }

    @Test
    @DisplayName("setParametriRisposta with responseEntity should extract status and headers")
    void testSetParametriRispostaWithResponseEntity() {
        NuovoEvento evento = mapper.createEvento(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID, DATA_START, DATA_END);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("X-Custom", "value1");
        ResponseEntity<String> responseEntity = new ResponseEntity<>("ok", httpHeaders, HttpStatus.OK);

        mapper.setParametriRisposta(evento, DATA_END, responseEntity, null);

        assertThat(evento.getParametriRisposta()).isNotNull();
        assertThat(evento.getParametriRisposta().getStatus()).isEqualTo(BigDecimal.valueOf(200));
        assertThat(evento.getParametriRisposta().getDataOraRisposta()).isEqualTo(DATA_END);
        assertThat(evento.getParametriRisposta().getHeaders()).isNotEmpty();
    }

    @Test
    @DisplayName("setParametriRisposta with HttpStatusCodeException should extract status and headers from exception")
    void testSetParametriRispostaWithHttpStatusCodeException() {
        NuovoEvento evento = mapper.createEvento(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID, DATA_START, DATA_END);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("X-Error", "details");
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", httpHeaders, null, null);

        mapper.setParametriRisposta(evento, DATA_END, null, exception);

        assertThat(evento.getParametriRisposta()).isNotNull();
        assertThat(evento.getParametriRisposta().getStatus()).isEqualTo(BigDecimal.valueOf(404));
        assertThat(evento.getParametriRisposta().getHeaders()).isNotEmpty();
    }

    @Test
    @DisplayName("setParametriRisposta with generic exception should default to status 500")
    void testSetParametriRispostaWithGenericException() {
        NuovoEvento evento = mapper.createEvento(COD_DOMINIO, TIPO_EVENTO, TRANSACTION_ID, DATA_START, DATA_END);
        RestClientException exception = new RestClientException("timeout");

        mapper.setParametriRisposta(evento, DATA_END, null, exception);

        assertThat(evento.getParametriRisposta()).isNotNull();
        assertThat(evento.getParametriRisposta().getStatus()).isEqualTo(BigDecimal.valueOf(500));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
