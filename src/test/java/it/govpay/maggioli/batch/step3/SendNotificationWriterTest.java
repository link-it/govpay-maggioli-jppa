package it.govpay.maggioli.batch.step3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;

import it.govpay.maggioli.batch.config.BatchProperties;
import it.govpay.maggioli.batch.entity.JppaConfig;
import it.govpay.maggioli.batch.repository.JppaConfigRepository;
import it.govpay.maggioli.batch.utils.CSVUtils;
import it.govpay.maggioli.client.model.RispostaNotificaPagamentoDto.EsitoEnum;

/**
 * Unit tests for SendNotificationWriter
 */
@ExtendWith(MockitoExtension.class)
class SendNotificationWriterTest {
    @Mock
    private JppaConfigRepository jppaConfigRepository;

    @Mock
    private BatchProperties batchProperties;

    private SendNotificationWriter writer;
	
    private final CSVUtils csvUtils = CSVUtils.getInstance(CSVFormat.DEFAULT);

    private static final String TEST_COD_DOMINIO = "12345678901";
    private static final String TEST_COD_CONNETTORE = "maggioli";
    private static final String TEST_CCP = "TEST_CCP";
    private static final String TEST_IUV = "TEST_IUV";
    private static final Instant TEST_MSG_RICEVUTA = Instant.parse("2025-01-27T11:30:00Z");
    private static final String TEST_STEP_NAME = "maggioliSendNotificationWorkerStep";

    @BeforeEach
    void setUp() throws Exception {
        writer = new SendNotificationWriter(jppaConfigRepository, batchProperties);
        File testReportDir = new File("/tmp/", "sendNotificationWriterTest");
        testReportDir.mkdir();
        when(batchProperties.getReportDir()).thenReturn(testReportDir.getAbsolutePath());

        // Simula l'iniezione di @Value da ExecutionContext usando reflection
        setField(writer, "codDominio", TEST_COD_DOMINIO);
    }

    @Test
    @DisplayName("Should write all complete data for domain")
    void testWriteAllCompleteData() throws Exception {
    	String baseReportName = "GOVPAY_" + TEST_COD_DOMINIO + "_";
    	File baseReportDir = new File(batchProperties.getReportDir());
    	File[] beforeReports = baseReportDir.listFiles(f -> f.getName().startsWith(baseReportName));
    	for (File r : beforeReports) {
			r.delete();
		}
        JppaConfig config = JppaConfig.builder().codDominio(TEST_COD_DOMINIO)
                                                .connettore(TEST_COD_CONNETTORE)
                                                .abilitato(Boolean.TRUE)
                                                .dataUltimaRt(Instant.parse("2025-01-27T10:30:00Z"))
                                                .build();
        when(jppaConfigRepository.findByCodDominio(TEST_COD_DOMINIO))
            .thenReturn(Optional.of(config));
        JppaConfig savedConfig = JppaConfig.builder().codDominio(TEST_COD_DOMINIO)
                                                     .connettore(TEST_COD_CONNETTORE)
                                                     .abilitato(Boolean.TRUE)
                                                     .dataUltimaRt(TEST_MSG_RICEVUTA)
                                                     .build();
        when(jppaConfigRepository.save(savedConfig))
            .thenReturn(savedConfig);

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
		writer.beforeStep(stepExecution);
        SendNotificationProcessor.NotificationCompleteData completeData =
            SendNotificationProcessor.NotificationCompleteData.builder()
                                                              .codDominio(TEST_COD_DOMINIO)
                                                              .ccp(TEST_CCP)
                                                              .iuv(TEST_IUV)
                                                              .esito(EsitoEnum.OK.name())
                                                              .dataMsgRicevuta(TEST_MSG_RICEVUTA)
                                                              .build();
        Chunk<? extends SendNotificationProcessor.NotificationCompleteData> chunk =
        		new Chunk<SendNotificationProcessor.NotificationCompleteData>(List.of(completeData));
        writer.write(chunk);
		writer.afterStep(stepExecution);
    	File[] afterReports = baseReportDir.listFiles(f -> f.getName().startsWith(baseReportName));

        // Then: Should write
        verify(jppaConfigRepository).findByCodDominio(TEST_COD_DOMINIO);
        verify(jppaConfigRepository).save(savedConfig);
        
        if (afterReports.length != 1)
            fail("Non è stato generato il report");
        try (ZipFile zipFd = new ZipFile(afterReports[0])) {
			Enumeration<? extends ZipEntry> zipEnum = zipFd.entries();
			ZipEntry zipEntry = zipEnum.nextElement();
			assertNotNull(zipEntry);
			InputStream entryIs = zipFd.getInputStream(zipEntry);
	    	List<byte[]> rows = CSVUtils.splitCSV(entryIs, 1);
	    	assertEquals(1, rows.size());
	    	CSVRecord record = csvUtils.getCSVRecord(new String(rows.get(0)));
	    	assertEquals(TEST_COD_DOMINIO, record.get(0));
	    	assertEquals(TEST_IUV, record.get(1));
	    	assertEquals(TEST_CCP, record.get(2));
	    	assertEquals(EsitoEnum.OK.name(), record.get(3));
	    	assertEquals("", record.get(4));
	    	assertEquals("", record.get(5));

	    	assertThat(!zipEnum.hasMoreElements());
		}
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
