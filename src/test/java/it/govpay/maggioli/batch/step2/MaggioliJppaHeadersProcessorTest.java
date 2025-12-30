package it.govpay.maggioli.batch.step2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.govpay.maggioli.batch.dto.DominioProcessingContext;
import it.govpay.maggioli.batch.dto.MaggioliHeadersBatch;
import it.govpay.maggioli.batch.repository.RptRepository;

/**
 * Unit tests for FdrHeadersProcessor
 */
@ExtendWith(MockitoExtension.class)
class MaggioliJppaHeadersProcessorTest {

    @Mock
    private RptRepository rptRepository;

    private MaggioliJppaHeadersProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MaggioliJppaHeadersProcessor(rptRepository);
    }

    @Test
    @DisplayName("Should process domain with multiple notifies")
    void testProcessWithMultipleFlows() throws Exception {
        // Given: Domain with 3 flows
        String codDominio = "12345678901";
        Instant lastRtDate = Instant.parse("2025-01-27T10:00:00Z");
        DominioProcessingContext context = DominioProcessingContext.builder()
            .codDominio(codDominio)
            .lastRtDate(lastRtDate)
            .build();

        List<MaggioliHeadersBatch.NotificaHeader> notifiche = new ArrayList<>();
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(1L).dataMsgRicevuta(Instant.parse("2025-01-27T10:30:00Z")).build());
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(2L).dataMsgRicevuta(Instant.parse("2025-01-27T11:30:00Z")).build());
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(3L).dataMsgRicevuta(Instant.parse("2025-01-27T12:30:00Z")).build());

        when(rptRepository.findIdsByCodDominioAndCodEsitoPagamentoInAndDataMsgRicevutaAfter(eq(codDominio), any(), eq(lastRtDate))).thenReturn(notifiche);

        // When: Process
        MaggioliHeadersBatch result = processor.process(context);

        // Then: Should return batch with 3 headers
        assertThat(result).isNotNull();
        assertThat(result.getCodDominio()).isEqualTo(codDominio);
        assertThat(result.getHeaders()).hasSize(3);

        // Verify first header
        MaggioliHeadersBatch.NotificaHeader header1 = result.getHeaders().get(0);
        assertThat(header1.getIdRpt()).isEqualTo(1L);
        assertThat(header1.getDataMsgRicevuta()).isNotNull();
    }

    @Test
    @DisplayName("Should return null when no flows found")
    void testProcessWithNoFlows() throws Exception {
        // Given: Domain with no new flows
        DominioProcessingContext context = DominioProcessingContext.builder()
            .codDominio("12345678901")
            .lastRtDate(Instant.now())
            .build();

        when(rptRepository.findIdsByCodDominioAndCodEsitoPagamentoInAndDataMsgRicevutaAfter(any(), any(), any())).thenReturn(List.of());

        // When: Process
        MaggioliHeadersBatch result = processor.process(context);

        // Then: Should return null (skip this domain)
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle domain with null lastRtDate")
    void testProcessWithNullLastPublicationDate() throws Exception {
        // Given: Domain with null last publication date (first acquisition)
        DominioProcessingContext context = DominioProcessingContext.builder()
            .codDominio("12345678901")
            .lastRtDate(null) // First time
            .build();

        List<MaggioliHeadersBatch.NotificaHeader> notifiche = new ArrayList<>();
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(1L).dataMsgRicevuta(Instant.parse("2025-01-27T10:30:00Z")).build());

        when(rptRepository.findIdsByCodDominioAndCodEsitoPagamentoIn(eq("12345678901"), any())).thenReturn(notifiche);

        // When: Process
        MaggioliHeadersBatch result = processor.process(context);

        // Then: Should process successfully
        assertThat(result).isNotNull();
        assertThat(result.getHeaders()).hasSize(1);
    }
}
