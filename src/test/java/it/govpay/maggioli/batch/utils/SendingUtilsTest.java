package it.govpay.maggioli.batch.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.govpay.maggioli.batch.entity.SingoloVersamento;
import it.govpay.maggioli.client.model.DatoAccertamentoDto;

class SendingUtilsTest {

    @Test
    @DisplayName("buildDatiAccertamento with 1 SV and 1 quota should return 1 DatoAccertamentoDto")
    void testBuildDatiAccertamentoWithValidData() {
        SingoloVersamento sv = SingoloVersamento.builder()
                .id(1L)
                .descrizione("Pagamento TARI")
                .contabilita("{\"quote\":[{\"capitolo\":\"CAP001\",\"annoEsercizio\":2025,\"importo\":150.50}]}")
                .build();

        List<DatoAccertamentoDto> result = SendingUtils.buildDatiAccertamento(Set.of(sv));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCodiceAccertamento()).isEqualTo("CAP001");
        assertThat(result.get(0).getAnnoAccertamento()).isEqualTo("2025");
        assertThat(result.get(0).getImportoAccertamento()).isEqualByComparingTo(new BigDecimal("150.50"));
        assertThat(result.get(0).getDescrizioneAccertamento()).isEqualTo("Pagamento TARI");
    }

    @Test
    @DisplayName("buildDatiAccertamento with 1 SV and 2 quote should return 2 DatoAccertamentoDto")
    void testBuildDatiAccertamentoWithMultipleQuote() {
        SingoloVersamento sv = SingoloVersamento.builder()
                .id(1L)
                .descrizione("Pagamento TARI")
                .contabilita("{\"quote\":[" +
                        "{\"capitolo\":\"CAP001\",\"annoEsercizio\":2025,\"importo\":100.00}," +
                        "{\"capitolo\":\"CAP002\",\"annoEsercizio\":2025,\"importo\":50.50}" +
                        "]}")
                .build();

        List<DatoAccertamentoDto> result = SendingUtils.buildDatiAccertamento(Set.of(sv));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("buildDatiAccertamento with 2 SV should return sum of all quote")
    void testBuildDatiAccertamentoWithMultipleSingoliVersamenti() {
        SingoloVersamento sv1 = SingoloVersamento.builder()
                .id(1L)
                .descrizione("Pagamento 1")
                .contabilita("{\"quote\":[{\"capitolo\":\"CAP001\",\"annoEsercizio\":2025,\"importo\":100.00}]}")
                .build();
        SingoloVersamento sv2 = SingoloVersamento.builder()
                .id(2L)
                .descrizione("Pagamento 2")
                .contabilita("{\"quote\":[{\"capitolo\":\"CAP002\",\"annoEsercizio\":2025,\"importo\":200.00}]}")
                .build();

        // Use LinkedHashSet for deterministic iteration order
        Set<SingoloVersamento> set = new LinkedHashSet<>();
        set.add(sv1);
        set.add(sv2);

        List<DatoAccertamentoDto> result = SendingUtils.buildDatiAccertamento(set);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("buildDatiAccertamento with empty quote should return empty list")
    void testBuildDatiAccertamentoWithEmptyQuote() {
        SingoloVersamento sv = SingoloVersamento.builder()
                .id(1L)
                .descrizione("Pagamento")
                .contabilita("{\"quote\":[]}")
                .build();

        List<DatoAccertamentoDto> result = SendingUtils.buildDatiAccertamento(Set.of(sv));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("buildDatiAccertamento with null input should return null")
    void testBuildDatiAccertamentoWithNullInput() {
        List<DatoAccertamentoDto> result = SendingUtils.buildDatiAccertamento(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("buildDatiAccertamento with invalid JSON should throw RuntimeException")
    void testBuildDatiAccertamentoWithInvalidJson() {
        SingoloVersamento sv = SingoloVersamento.builder()
                .id(1L)
                .descrizione("Pagamento")
                .contabilita("not-valid-json")
                .build();

        assertThatThrownBy(() -> SendingUtils.buildDatiAccertamento(Set.of(sv)))
                .isInstanceOf(RuntimeException.class);
    }
}
