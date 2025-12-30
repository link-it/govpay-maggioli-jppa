package it.govpay.maggioli.batch.step2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import it.govpay.maggioli.batch.dto.MaggioliHeadersBatch;
import it.govpay.maggioli.batch.entity.JppaNotifiche;
import it.govpay.maggioli.batch.repository.JppaNotificheRepository;

/**
 * Unit tests for FdrHeadersWriter
 */
@ExtendWith(MockitoExtension.class)
class MaggioliJppaHeadersWriterTest {

    @Mock
    private JppaNotificheRepository jppaNotificheRepository;

    @Captor
    private ArgumentCaptor<JppaNotifiche> jppaNotificheCaptor;

    private MaggioliJppaHeadersWriter writer;

    @BeforeEach
    void setUp() {
        writer = new MaggioliJppaHeadersWriter(jppaNotificheRepository);
    }

    @Test
    @DisplayName("Should write all headers to JPPA_NOTIFICHE")
    void testWriteMultipleHeaders() throws Exception {
        // Given: Batch with 3 headers (all new)
        String codDominio = "12345678901";
        List<MaggioliHeadersBatch.NotificaHeader> notifiche = new ArrayList<>();
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(1L).dataMsgRicevuta(Instant.parse("2025-01-27T10:30:00Z")).build());
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(2L).dataMsgRicevuta(Instant.parse("2025-01-27T11:30:00Z")).build());
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(3L).dataMsgRicevuta(Instant.parse("2025-01-27T12:30:00Z")).build());

        MaggioliHeadersBatch batch = MaggioliHeadersBatch.builder()
            .codDominio(codDominio)
            .headers(notifiche)
            .build();

        Chunk<MaggioliHeadersBatch> chunk = new Chunk<>(List.of(batch));

        when(jppaNotificheRepository.existsByIdRptAndCodDominio(any(), any())).thenReturn(false);

        // When: Write
        writer.write(chunk);

        // Then: Should save all 3 headers
        verify(jppaNotificheRepository, times(3)).save(any(JppaNotifiche.class));
        verify(jppaNotificheRepository, times(3)).existsByIdRptAndCodDominio(any(), any());
    }

    @Test
    @DisplayName("Should skip duplicate headers")
    void testWriteSkipsDuplicates() throws Exception {
        // Given: Batch with 3 headers (2 new, 1 duplicate)
        String codDominio = "12345678901";
        List<MaggioliHeadersBatch.NotificaHeader> notifiche = new ArrayList<>();
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(1L).dataMsgRicevuta(Instant.parse("2025-01-27T10:30:00Z")).build());
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(1L).dataMsgRicevuta(Instant.parse("2025-01-27T11:30:00Z")).build());
        notifiche.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(3L).dataMsgRicevuta(Instant.parse("2025-01-27T12:30:00Z")).build());

        MaggioliHeadersBatch batch = MaggioliHeadersBatch.builder()
            .codDominio(codDominio)
            .headers(notifiche)
            .build();

        Chunk<MaggioliHeadersBatch> chunk = new Chunk<>(List.of(batch));

        // RPT 1L already exists in JPPA_NOTIFICHE
        AtomicInteger counter = new AtomicInteger(0);
        when(jppaNotificheRepository.existsByIdRptAndCodDominio(eq(1L), eq(codDominio))).thenAnswer(invocation -> {
        	if (invocation.getArgument(0, Long.class).longValue() == 1) {
	        	if (counter.incrementAndGet() == 1)
	        		return false;
	        	return true;
        	}
        	return false;
        });

        // When: Write
        writer.write(chunk);

        // Then: Should save only 2 headers (skip duplicate in JPPA_NOTIFICHE)
        verify(jppaNotificheRepository, times(2)).save(any(JppaNotifiche.class));
    }

    @Test
    @DisplayName("Should write multiple domains in single chunk")
    void testWriteMultipleDomains() throws Exception {
        // Given: Chunk with 2 domains
        List<MaggioliHeadersBatch.NotificaHeader> notifiche1 = new ArrayList<>();
        notifiche1.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(1L).dataMsgRicevuta(Instant.parse("2025-01-27T10:30:00Z")).build());
        MaggioliHeadersBatch batch1 = MaggioliHeadersBatch.builder()
            .codDominio("12345678901")
            .headers(notifiche1)
            .build();

        List<MaggioliHeadersBatch.NotificaHeader> notifiche2 = new ArrayList<>();
        notifiche2.add(MaggioliHeadersBatch.NotificaHeader.builder().idRpt(2L).dataMsgRicevuta(Instant.parse("2025-01-27T11:30:00Z")).build());
        MaggioliHeadersBatch batch2 = MaggioliHeadersBatch.builder()
            .codDominio("12345678902")
            .headers(notifiche2)
            .build();

        Chunk<MaggioliHeadersBatch> chunk = new Chunk<>(List.of(batch1, batch2));

        when(jppaNotificheRepository.existsByIdRptAndCodDominio(any(), any())).thenReturn(false);

        // When: Write
        writer.write(chunk);

        // Then: Should save both batches
        verify(jppaNotificheRepository, times(2)).save(any(JppaNotifiche.class));
    }

    @Test
    @DisplayName("Should handle empty chunk gracefully")
    void testWriteEmptyChunk() throws Exception {
        // Given: Empty chunk
        Chunk<MaggioliHeadersBatch> chunk = new Chunk<>();

        // When: Write
        writer.write(chunk);

        // Then: Should not save anything
        verify(jppaNotificheRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle batch with empty headers list")
    void testWriteBatchWithEmptyHeaders() throws Exception {
        // Given: Batch with empty headers list
        MaggioliHeadersBatch batch = MaggioliHeadersBatch.builder()
            .codDominio("12345678901")
            .headers(List.of())
            .build();

        Chunk<MaggioliHeadersBatch> chunk = new Chunk<>(List.of(batch));

        // When: Write
        writer.write(chunk);

        // Then: Should not save anything
        verify(jppaNotificheRepository, never()).save(any());
    }
}
