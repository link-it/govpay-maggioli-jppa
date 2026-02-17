package it.govpay.maggioli.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Batch of Maggioli JPPA headers retrieved from pagoPA API
 */
@Data
@Builder
public class MaggioliHeadersBatch {
    private String codDominio;
    private List<NotificaHeader> headers;

    @Data
    @Builder
    public static class NotificaHeader {
        private Long idRpt;
        private Instant dataMsgRicevuta;
    }
}
