package it.govpay.maggioli.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Context information for processing a single domain
 */
@Data
@Builder
public class DominioProcessingContext {
    private String codDominio;
    private String codConnettore;
    private Instant lastRtDate;
}
