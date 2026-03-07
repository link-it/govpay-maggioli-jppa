package it.govpay.maggioli.batch.step3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.mail.MailSendException;

import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.mail.MailInfo;
import it.govpay.maggioli.batch.entity.JppaConfig;
import it.govpay.maggioli.batch.repository.JppaConfigRepository;
import it.govpay.maggioli.batch.service.MaggioliMailService;
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
    private ConnettoreService connettoreService;

    @Mock
    private MaggioliMailService mailService;

    private SendNotificationWriter writer;

    private final CSVUtils csvUtils = CSVUtils.getInstance(CSVFormat.DEFAULT);

    private static final String TEST_COD_DOMINIO = "12345678901";
    private static final String TEST_COD_CONNETTORE = "maggioli";
    private static final String TEST_CCP = "TEST_CCP";
    private static final String TEST_IUV = "TEST_IUV";
    private static final String TEST_EMAIL = "test@example.com";
    private static final Instant TEST_MSG_RICEVUTA = Instant.parse("2025-01-27T11:30:00Z");
    private static final String TEST_STEP_NAME = "maggioliSendNotificationWorkerStep";
    private static final String TEST_REPORT_DIR = "/tmp/sendNotificationWriterTest";

    @BeforeEach
    void setUp() throws Exception {
        writer = new SendNotificationWriter(jppaConfigRepository, connettoreService, mailService);

        new File(TEST_REPORT_DIR).mkdir();

        // Simula l'iniezione di @Value da ExecutionContext usando reflection
        setField(writer, "codDominio", TEST_COD_DOMINIO);
        setField(writer, "codConnettore", TEST_COD_CONNETTORE);
    }

    @AfterEach
    void tearDown() {
        // Pulizia file ZIP generati dai test
        File dir = new File(TEST_REPORT_DIR);
        File[] zips = dir.listFiles(f -> f.getName().startsWith("GOVPAY_" + TEST_COD_DOMINIO + "_"));
        if (zips != null) {
            Arrays.stream(zips).forEach(File::delete);
        }
        dir.delete();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void stubConnettore(Map<String, String> props) {
        when(connettoreService.getConnettoreAsMap(TEST_COD_CONNETTORE)).thenReturn(props);
    }

    private void stubJppaConfig() {
        JppaConfig config = JppaConfig.builder()
                .codDominio(TEST_COD_DOMINIO)
                .connettore(TEST_COD_CONNETTORE)
                .abilitato(Boolean.TRUE)
                .dataUltimaRt(Instant.parse("2025-01-27T10:30:00Z"))
                .build();
        when(jppaConfigRepository.findByCodDominio(TEST_COD_DOMINIO)).thenReturn(Optional.of(config));

        JppaConfig savedConfig = JppaConfig.builder()
                .codDominio(TEST_COD_DOMINIO)
                .connettore(TEST_COD_CONNETTORE)
                .abilitato(Boolean.TRUE)
                .dataUltimaRt(TEST_MSG_RICEVUTA)
                .build();
        when(jppaConfigRepository.save(savedConfig)).thenReturn(savedConfig);
    }

    private SendNotificationProcessor.NotificationCompleteData buildCompleteData() {
        return SendNotificationProcessor.NotificationCompleteData.builder()
                .codDominio(TEST_COD_DOMINIO)
                .ccp(TEST_CCP)
                .iuv(TEST_IUV)
                .esito(EsitoEnum.OK.name())
                .dataMsgRicevuta(TEST_MSG_RICEVUTA)
                .build();
    }

    // -------------------------------------------------------------------------
    // Test scrittura CSV/ZIP
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Scrittura corretta del CSV nel ZIP")
    void testWriteAllCompleteData() throws Exception {
        stubConnettore(Map.of(
            "INVIA_TRACCIATO_ESITO", "true",
            "FILE_SYSTEM_PATH", TEST_REPORT_DIR
        ));
        stubJppaConfig();

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
        writer.beforeStep(stepExecution);
        writer.write(new Chunk<>(List.of(buildCompleteData())));
        writer.afterStep(stepExecution);

        File[] reports = new File(TEST_REPORT_DIR).listFiles(f -> f.getName().startsWith("GOVPAY_" + TEST_COD_DOMINIO + "_"));
        if (reports == null || reports.length != 1)
            fail("Non è stato generato il report");

        try (ZipFile zipFd = new ZipFile(reports[0])) {
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

        verify(jppaConfigRepository).findByCodDominio(TEST_COD_DOMINIO);
        verify(jppaConfigRepository).save(any());
        verify(mailService, never()).inviaEmail(any());
    }

    // -------------------------------------------------------------------------
    // Test invio email
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Email con ZIP allegato inviata dopo chiusura del report")
    void testEmailInviataDopoChiusuraZip() throws Exception {
        stubConnettore(Map.of(
            "INVIA_TRACCIATO_ESITO", "true",
            "FILE_SYSTEM_PATH", TEST_REPORT_DIR,
            "EMAIL_ALLEGATO", "true",
            "EMAIL_INDIRIZZO", TEST_EMAIL
        ));
        stubJppaConfig();
        when(mailService.isAbilitato()).thenReturn(true);

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
        writer.beforeStep(stepExecution);
        writer.write(new Chunk<>(List.of(buildCompleteData())));
        writer.afterStep(stepExecution);

        ArgumentCaptor<MailInfo> captor = ArgumentCaptor.forClass(MailInfo.class);
        verify(mailService).inviaEmail(captor.capture());

        MailInfo mailInfo = captor.getValue();
        assertEquals(List.of(TEST_EMAIL), mailInfo.getTo());
        // Oggetto: costruito dal default con tipo tracciato e data ultima ricevuta
        assertThat(mailInfo.getOggetto()).contains("inviati al servizio Maggioli JPPA");
        assertThat(mailInfo.getOggetto()).contains("27/01/2025");
        // Corpo: contiene i dati del tracciato
        assertThat(mailInfo.getTesto()).contains("Ente Creditore: " + TEST_COD_DOMINIO);
        assertThat(mailInfo.getTesto()).contains("Numero pagamenti: 1");
        assertThat(mailInfo.getTesto()).contains("in allegato alla presente");
        // Allegato: ZIP generato
        assertNotNull(mailInfo.getAllegati());
        assertEquals(1, mailInfo.getAllegati().size());
        String nomeAllegato = mailInfo.getAllegati().keySet().iterator().next();
        assertThat(nomeAllegato).startsWith("GOVPAY_" + TEST_COD_DOMINIO + "_").endsWith(".zip");
    }

    @Test
    @DisplayName("Email con oggetto personalizzato da EMAIL_SUBJECT del connettore")
    void testEmailSubjectPersonalizzato() throws Exception {
        String oggettoCustom = "Report personalizzato";
        stubConnettore(Map.of(
            "INVIA_TRACCIATO_ESITO", "true",
            "FILE_SYSTEM_PATH", TEST_REPORT_DIR,
            "EMAIL_ALLEGATO", "true",
            "EMAIL_INDIRIZZO", TEST_EMAIL,
            "EMAIL_SUBJECT", oggettoCustom
        ));
        stubJppaConfig();
        when(mailService.isAbilitato()).thenReturn(true);

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
        writer.beforeStep(stepExecution);
        writer.write(new Chunk<>(List.of(buildCompleteData())));
        writer.afterStep(stepExecution);

        ArgumentCaptor<MailInfo> captor = ArgumentCaptor.forClass(MailInfo.class);
        verify(mailService).inviaEmail(captor.capture());
        assertEquals(oggettoCustom, captor.getValue().getOggetto());
        assertThat(captor.getValue().getTesto()).contains("Ente Creditore: " + TEST_COD_DOMINIO);
    }

    @Test
    @DisplayName("Email non inviata se INVIA_TRACCIATO_ESITO=false")
    void testEmailNonInviataSeTracciatoDisabilitato() throws Exception {
        stubConnettore(Map.of(
            "INVIA_TRACCIATO_ESITO", "false"
        ));

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
        writer.beforeStep(stepExecution);
        writer.afterStep(stepExecution);

        verify(mailService, never()).inviaEmail(any());
    }

    @Test
    @DisplayName("Email inviata senza ZIP allegato se EMAIL_ALLEGATO=false")
    void testEmailInviataSenzaAllegato() throws Exception {
        stubConnettore(Map.of(
            "INVIA_TRACCIATO_ESITO", "true",
            "FILE_SYSTEM_PATH", TEST_REPORT_DIR,
            "EMAIL_ALLEGATO", "false",
            "EMAIL_INDIRIZZO", TEST_EMAIL
        ));
        stubJppaConfig();
        when(mailService.isAbilitato()).thenReturn(true);

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
        writer.beforeStep(stepExecution);
        writer.write(new Chunk<>(List.of(buildCompleteData())));
        writer.afterStep(stepExecution);

        ArgumentCaptor<MailInfo> captor = ArgumentCaptor.forClass(MailInfo.class);
        verify(mailService).inviaEmail(captor.capture());
        assertEquals(List.of(TEST_EMAIL), captor.getValue().getTo());
        assertThat(captor.getValue().getAllegati()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Con più destinatari, il primo va in To e gli altri in CC")
    void testEmailPiuDestinatariToCc() throws Exception {
        String email2 = "secondo@example.com";
        String email3 = "terzo@example.com";
        stubConnettore(Map.of(
            "INVIA_TRACCIATO_ESITO", "true",
            "FILE_SYSTEM_PATH", TEST_REPORT_DIR,
            "EMAIL_INDIRIZZO", TEST_EMAIL + "," + email2 + "," + email3
        ));
        stubJppaConfig();
        when(mailService.isAbilitato()).thenReturn(true);

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
        writer.beforeStep(stepExecution);
        writer.write(new Chunk<>(List.of(buildCompleteData())));
        writer.afterStep(stepExecution);

        ArgumentCaptor<MailInfo> captor = ArgumentCaptor.forClass(MailInfo.class);
        verify(mailService).inviaEmail(captor.capture());
        assertEquals(List.of(TEST_EMAIL), captor.getValue().getTo());
        assertEquals(List.of(email2, email3), captor.getValue().getCc());
    }

    @Test
    @DisplayName("Email non inviata se il servizio mail non è abilitato")
    void testEmailNonInviataSeServizioMailDisabilitato() throws Exception {
        stubConnettore(Map.of(
            "INVIA_TRACCIATO_ESITO", "true",
            "FILE_SYSTEM_PATH", TEST_REPORT_DIR,
            "EMAIL_ALLEGATO", "true",
            "EMAIL_INDIRIZZO", TEST_EMAIL
        ));
        when(mailService.isAbilitato()).thenReturn(false);

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
        writer.beforeStep(stepExecution);
        writer.afterStep(stepExecution);

        verify(mailService, never()).inviaEmail(any());
    }

    @Test
    @DisplayName("Email non inviata se nessun destinatario configurato")
    void testEmailNonInviataSenzaDestinatari() throws Exception {
        stubConnettore(Map.of(
            "INVIA_TRACCIATO_ESITO", "true",
            "FILE_SYSTEM_PATH", TEST_REPORT_DIR,
            "EMAIL_ALLEGATO", "true",
            "EMAIL_INDIRIZZO", ""
        ));
        when(mailService.isAbilitato()).thenReturn(true);

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
        writer.beforeStep(stepExecution);
        writer.afterStep(stepExecution);

        verify(mailService, never()).inviaEmail(any());
    }

    @Test
    @DisplayName("Errore nell'invio email non propaga e lo step completa")
    void testErroreInvioEmailNonPropaga() throws Exception {
        stubConnettore(Map.of(
            "INVIA_TRACCIATO_ESITO", "true",
            "FILE_SYSTEM_PATH", TEST_REPORT_DIR,
            "EMAIL_ALLEGATO", "true",
            "EMAIL_INDIRIZZO", TEST_EMAIL
        ));
        when(mailService.isAbilitato()).thenReturn(true);
        doThrow(new MailSendException("SMTP non raggiungibile")).when(mailService).inviaEmail(any());

        StepExecution stepExecution = new StepExecution(TEST_STEP_NAME, null);
        writer.beforeStep(stepExecution);

        assertDoesNotThrow(() -> writer.afterStep(stepExecution));
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
