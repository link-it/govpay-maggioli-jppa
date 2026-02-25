package it.govpay.maggioli.batch.gde.mapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gde.client.beans.RuoloEvento;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EventoMaggioliMapper {

    @Value("${govpay.batch.cluster-id}")
    private String clusterId;

    public NuovoEvento createEvento(String codDominio, String tipoEvento, String transactionId,
                                     OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        NuovoEvento nuovoEvento = new NuovoEvento();

        nuovoEvento.setIdDominio(codDominio);
        nuovoEvento.setCategoriaEvento(CategoriaEvento.INTERFACCIA);
        nuovoEvento.setClusterId(clusterId);
        nuovoEvento.setDataEvento(dataStart);
        nuovoEvento.setDurataEvento(dataEnd.toInstant().toEpochMilli() - dataStart.toInstant().toEpochMilli());
        nuovoEvento.setRuolo(RuoloEvento.CLIENT);
        nuovoEvento.setComponente(ComponenteEvento.API_MAGGIOLI_JPPA);
        nuovoEvento.setTipoEvento(tipoEvento);
        nuovoEvento.setTransactionId(transactionId);

        return nuovoEvento;
    }

    public NuovoEvento createEventoOk(String codDominio, String tipoEvento, String transactionId,
                                       OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        NuovoEvento nuovoEvento = createEvento(codDominio, tipoEvento, transactionId, dataStart, dataEnd);
        nuovoEvento.setEsito(EsitoEvento.OK);
        return nuovoEvento;
    }

    public NuovoEvento createEventoKo(String codDominio, String tipoEvento, String transactionId,
                                       OffsetDateTime dataStart, OffsetDateTime dataEnd,
                                       ResponseEntity<?> responseEntity, RestClientException exception) {
        NuovoEvento nuovoEvento = createEvento(codDominio, tipoEvento, transactionId, dataStart, dataEnd);
        extractExceptionInfo(responseEntity, exception, nuovoEvento);
        return nuovoEvento;
    }

    public void setParametriRichiesta(NuovoEvento nuovoEvento, String urlOperazione,
                                       String httpMethod, List<Header> headers) {
        DettaglioRichiesta dettaglioRichiesta = new DettaglioRichiesta();
        dettaglioRichiesta.setDataOraRichiesta(nuovoEvento.getDataEvento());
        dettaglioRichiesta.setMethod(httpMethod);
        dettaglioRichiesta.setUrl(urlOperazione);
        dettaglioRichiesta.setHeaders(headers);

        nuovoEvento.setParametriRichiesta(dettaglioRichiesta);
    }

    public void setParametriRisposta(NuovoEvento nuovoEvento, OffsetDateTime dataEnd,
                                      ResponseEntity<?> responseEntity, RestClientException exception) {
        DettaglioRisposta dettaglioRisposta = new DettaglioRisposta();
        dettaglioRisposta.setDataOraRisposta(dataEnd);

        List<Header> headers = new ArrayList<>();

        if (responseEntity != null) {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(responseEntity.getStatusCode().value()));

            HttpHeaders httpHeaders = responseEntity.getHeaders();
            httpHeaders.forEach((key, value) -> {
                if (!value.isEmpty()) {
                    Header header = new Header();
                    header.setNome(key);
                    header.setValore(value.get(0));
                    headers.add(header);
                }
            });
        } else if (exception instanceof HttpStatusCodeException httpStatusCodeException) {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(httpStatusCodeException.getStatusCode().value()));

            HttpHeaders httpHeaders = httpStatusCodeException.getResponseHeaders();
            if (httpHeaders != null) {
                httpHeaders.forEach((key, value) -> {
                    if (!value.isEmpty()) {
                        Header header = new Header();
                        header.setNome(key);
                        header.setValore(value.get(0));
                        headers.add(header);
                    }
                });
            }
        } else {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(500));
        }

        dettaglioRisposta.setHeaders(headers);
        nuovoEvento.setParametriRisposta(dettaglioRisposta);
    }

    private void extractExceptionInfo(ResponseEntity<?> responseEntity, RestClientException exception,
                                       NuovoEvento nuovoEvento) {
        if (exception != null) {
            if (exception instanceof HttpStatusCodeException httpStatusCodeException) {
                nuovoEvento.setDettaglioEsito(httpStatusCodeException.getResponseBodyAsString());
                nuovoEvento.setSottotipoEsito(httpStatusCodeException.getStatusCode().value() + "");

                if (httpStatusCodeException.getStatusCode().is5xxServerError()) {
                    nuovoEvento.setEsito(EsitoEvento.FAIL);
                } else {
                    nuovoEvento.setEsito(EsitoEvento.KO);
                }
            } else {
                nuovoEvento.setDettaglioEsito(exception.getMessage());
                nuovoEvento.setSottotipoEsito("500");
                nuovoEvento.setEsito(EsitoEvento.FAIL);
            }
        } else if (responseEntity != null) {
            nuovoEvento.setDettaglioEsito(HttpStatus.valueOf(responseEntity.getStatusCode().value()).getReasonPhrase());
            nuovoEvento.setSottotipoEsito("" + responseEntity.getStatusCode().value());

            if (responseEntity.getStatusCode().is5xxServerError()) {
                nuovoEvento.setEsito(EsitoEvento.FAIL);
            } else {
                nuovoEvento.setEsito(EsitoEvento.KO);
            }
        }
    }
}
