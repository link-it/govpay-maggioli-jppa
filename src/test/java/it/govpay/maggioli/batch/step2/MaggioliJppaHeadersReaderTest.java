package it.govpay.maggioli.batch.step2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.govpay.maggioli.batch.dto.DominioProcessingContext;
import it.govpay.maggioli.batch.entity.JppaConfig;
import it.govpay.maggioli.batch.repository.JppaConfigRepository;

/**
 * Unit tests for FdrHeadersReader
 */
@ExtendWith(MockitoExtension.class)
class MaggioliJppaHeadersReaderTest {

    @Mock
    private JppaConfigRepository jppaConfigRepository;

    private MaggioliJppaHeadersReader reader;

    @BeforeEach
    void setUp() {
        // Reset static queue before each test
    	MaggioliJppaHeadersReader.resetQueue();
        reader = new MaggioliJppaHeadersReader(jppaConfigRepository);
    }

    @Test
    @DisplayName("Should read all domains and return DominioProcessingContext")
    void testReadMultipleDomains() throws Exception {
        // Given: 3 domains with last publication dates
        Instant instant1 = Instant.parse("2025-01-27T10:00:00Z");
        JppaConfig jppaConfig1 = JppaConfig.builder().codDominio("12345678901").dataUltimaRt(instant1).build();

        Instant instant2 = Instant.parse("2025-01-27T11:00:00Z");
        JppaConfig jppaConfig2 = JppaConfig.builder().codDominio("12345678902").dataUltimaRt(instant2).build();

        Instant instant3 = null; // No previous acquisition
        JppaConfig jppaConfig3 = JppaConfig.builder().codDominio("12345678903").dataUltimaRt(instant3).build();

        when(jppaConfigRepository.findAllByAbilitato(anyBoolean())).thenReturn(List.of(jppaConfig1, jppaConfig2, jppaConfig3));

        // When: Read all domains
        DominioProcessingContext ctx1 = reader.read();
        DominioProcessingContext ctx2 = reader.read();
        DominioProcessingContext ctx3 = reader.read();
        DominioProcessingContext ctx4 = reader.read(); // Should be null

        // Then: Should return all 3 domains then null
        assertThat(ctx1).isNotNull();
        assertThat(ctx1.getCodDominio()).isEqualTo("12345678901");
        assertThat(ctx1.getLastRtDate()).isEqualTo(instant1);

        assertThat(ctx2).isNotNull();
        assertThat(ctx2.getCodDominio()).isEqualTo("12345678902");
        assertThat(ctx2.getLastRtDate()).isEqualTo(instant2);

        assertThat(ctx3).isNotNull();
        assertThat(ctx3.getCodDominio()).isEqualTo("12345678903");
        assertThat(ctx3.getLastRtDate()).isNull();

        assertThat(ctx4).isNull(); // End of data

        // Verify repository was called only once (on first read)
        verify(jppaConfigRepository, times(1)).findAllByAbilitato(anyBoolean());
    }

    @Test
    @DisplayName("Should return null when no domains found")
    void testReadNoDomains() throws Exception {
        // Given: No domains
        when(jppaConfigRepository.findAllByAbilitato(anyBoolean())).thenReturn(List.of());

        // When: Read
        DominioProcessingContext result = reader.read();

        // Then: Should return null immediately
        assertThat(result).isNull();
        verify(jppaConfigRepository, times(1)).findAllByAbilitato(anyBoolean());
    }

    @Test
    @DisplayName("Should handle single domain")
    void testReadSingleDomain() throws Exception {
        // Given: Single domain
        Instant instant = Instant.parse("2025-01-27T10:00:00Z");
        JppaConfig jppaConfig = JppaConfig.builder().codDominio("12345678901").dataUltimaRt(instant).build();

        when(jppaConfigRepository.findAllByAbilitato(anyBoolean())).thenReturn(List.of(jppaConfig));

        // When: Read twice
        DominioProcessingContext ctx1 = reader.read();
        DominioProcessingContext ctx2 = reader.read();

        // Then: First should return domain, second should be null
        assertThat(ctx1).isNotNull();
        assertThat(ctx1.getCodDominio()).isEqualTo("12345678901");
        assertThat(ctx2).isNull();
    }

    @Test
    @DisplayName("Should initialize only once on first read")
    void testInitializeOnce() throws Exception {
        // Given
        Instant instant = Instant.parse("2025-01-27T10:00:00Z");
        JppaConfig jppaConfig = JppaConfig.builder().codDominio("12345678901").dataUltimaRt(instant).build();

        when(jppaConfigRepository.findAllByAbilitato(anyBoolean())).thenReturn(List.of(jppaConfig));

        // When: Read multiple times
        reader.read();
        reader.read();
        reader.read();

        // Then: Repository should be called only once
        verify(jppaConfigRepository, times(1)).findAllByAbilitato(anyBoolean());
    }
}
