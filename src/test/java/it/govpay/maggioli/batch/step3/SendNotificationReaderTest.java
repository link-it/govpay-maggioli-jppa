package it.govpay.maggioli.batch.step3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;

import it.govpay.maggioli.batch.Costanti;
import it.govpay.maggioli.batch.entity.RPT;
import it.govpay.maggioli.batch.entity.Versamento;
import it.govpay.maggioli.batch.repository.RptRepository;

/**
 * Unit tests for SendNotificationReader (partitioner-based)
 */
@ExtendWith(MockitoExtension.class)
class SendNotificationReaderTest {
	private static final String XML_RT = "XML_RT_TEST";

    @Mock
    private RptRepository rptRepository;

    private SendNotificationReader reader;

    private static final String TEST_COD_DOMINIO = "12345678901";
    private static final int TEST_PARTITION_NUMBER = 1;
    private static final int TEST_TOTAL_PARTITIONS = 5;

    @BeforeEach
    void setUp() throws Exception {
        reader = new SendNotificationReader(rptRepository);

        // Simula l'iniezione di @Value da ExecutionContext usando reflection
        setField(reader, "codDominio", TEST_COD_DOMINIO);
        setField(reader, "partitionNumber", TEST_PARTITION_NUMBER);
        setField(reader, "totalPartitions", TEST_TOTAL_PARTITIONS);
    }

    @Test
    @DisplayName("Should read all receipt for assigned domain")
    void testReadAllRptForDomain() throws Exception {
        // Given: 10 flows for the domain
        List<RPT> receipts = createRptList(10, TEST_COD_DOMINIO);
        when(rptRepository.findByNotificheOrderByDataMsgRicevuta(TEST_COD_DOMINIO))
            .thenReturn(receipts);

        // When: Open reader and read all
        reader.open(new ExecutionContext());

        List<RPT> results = new ArrayList<>();
        RPT rptTemp;
        while ((rptTemp = reader.read()) != null) {
            results.add(rptTemp);
        }

        // Then: Should read all 10 flows
        assertThat(results).hasSize(10);
        verify(rptRepository).findByNotificheOrderByDataMsgRicevuta(TEST_COD_DOMINIO);
    }

    @Test
    @DisplayName("Should return null when domain has no recepit to be notify")
    void testReadNoRpts() throws Exception {
        // Given: Empty list for domain
        when(rptRepository.findByNotificheOrderByDataMsgRicevuta(TEST_COD_DOMINIO))
            .thenReturn(new ArrayList<>());

        // When: Open and read
        reader.open(new ExecutionContext());
        RPT result = reader.read();

        // Then: Should return null immediately
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should read receipts in correct order")
    void testReadInOrder() throws Exception {
        // Given: Flows with sequential codes
        List<RPT> receipts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RPT rptTemp = RPT.builder()
                    .id((long)i)
                    .versamento(Versamento.builder().id((long)(100 + i)).build())
                    .codDominio(TEST_COD_DOMINIO)
                    .stato("RPT_RICEVUTA_NODO")
                    .ccp("CCP_" + String.format("%03d", i))
                    .iuv("IUV_" + String.format("%03d", i))
                    .codEsitoPagamento(Costanti.RPT_ESITO_PAGAMENTO_ESEGUITO)
                    .dataMsgRicevuta(Instant.now())
                    .xmlRt(XML_RT.getBytes())
                    .build();
            receipts.add(rptTemp);
        }

        when(rptRepository.findByNotificheOrderByDataMsgRicevuta(TEST_COD_DOMINIO))
            .thenReturn(receipts);

        // When: Read all
        reader.open(new ExecutionContext());
        List<RPT> results = new ArrayList<>();
        RPT rptTemp;
        while ((rptTemp = reader.read()) != null) {
            results.add(rptTemp);
        }

        // Then: Should maintain order
        assertThat(results).hasSize(5);
        for (int i = 0; i < 5; i++) {
            assertThat(results.get(i).getIuv()).isEqualTo("IUV_" + String.format("%03d", i));
        }
    }

    @Test
    @DisplayName("Should handle single receipt")
    void testReadSingleFlow() throws Exception {
        // Given: Single flow
        List<RPT> receipts = createRptList(1, TEST_COD_DOMINIO);
        when(rptRepository.findByNotificheOrderByDataMsgRicevuta(TEST_COD_DOMINIO))
            .thenReturn(receipts);

        // When: Read
        reader.open(new ExecutionContext());
        RPT first = reader.read();
        RPT second = reader.read();

        // Then: First should have value, second should be null
        assertThat(first).isNotNull();
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("Should only initialize once on open")
    void testInitializeOnce() throws Exception {
        // Given
        List<RPT> receipts = createRptList(3, TEST_COD_DOMINIO);
        when(rptRepository.findByNotificheOrderByDataMsgRicevuta(TEST_COD_DOMINIO))
            .thenReturn(receipts);

        // When: Open and read multiple times
        reader.open(new ExecutionContext());
        reader.read();
        reader.read();
        reader.read();
        reader.read(); // Should return null

        // Then: Repository should be called only once
        verify(rptRepository).findByNotificheOrderByDataMsgRicevuta(TEST_COD_DOMINIO);
    }

    @Test
    @DisplayName("Should handle close properly")
    void testClose() throws Exception {
        // Given
        List<RPT> receipts = createRptList(5, TEST_COD_DOMINIO);
        when(rptRepository.findByNotificheOrderByDataMsgRicevuta(TEST_COD_DOMINIO))
            .thenReturn(receipts);

        // When: Open, read some, then close
        reader.open(new ExecutionContext());
        reader.read();
        reader.close();

        // Then: Should not throw exception
        // Further reads after close would require reopen
    }

    @Test
    @DisplayName("Should handle large dataset for single domain")
    void testReadLargeDataset() throws Exception {
        // Given: 100 flows for one domain
        List<RPT> flussi = createRptList(100, TEST_COD_DOMINIO);
        when(rptRepository.findByNotificheOrderByDataMsgRicevuta(TEST_COD_DOMINIO))
            .thenReturn(flussi);

        // When: Read all
        reader.open(new ExecutionContext());
        int count = 0;
        while (reader.read() != null) {
            count++;
        }

        // Then: Should read all 100 flows
        assertThat(count).isEqualTo(100);
    }

    private List<RPT> createRptList(int size, String codDominio) {
        List<RPT> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            RPT rptTemp = RPT.builder()
                .id((long)i)
                .versamento(Versamento.builder().id((long)(100 + i)).build())
                .codDominio(codDominio)
                .stato("RPT_RICEVUTA_NODO")
                .ccp("CCP_"+ i)
                .iuv("IUV_" + i)
                .codEsitoPagamento(Costanti.RPT_ESITO_PAGAMENTO_ESEGUITO)
                .dataMsgRicevuta(Instant.now())
                .xmlRt(XML_RT.getBytes())
                .build();
            list.add(rptTemp);
        }
        return list;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
