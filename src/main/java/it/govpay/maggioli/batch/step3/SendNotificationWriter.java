package it.govpay.maggioli.batch.step3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.csv.CSVFormat;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.mail.MailInfo;
import it.govpay.common.utils.ConnettoreMapUtils;
import it.govpay.maggioli.batch.Costanti;
import it.govpay.maggioli.batch.entity.JppaConfig;
import it.govpay.maggioli.batch.repository.JppaConfigRepository;
import it.govpay.maggioli.batch.service.MaggioliMailService;
import it.govpay.maggioli.batch.utils.CSVUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Writer to save report complete data
 */
@Component
@StepScope
@Slf4j
public class SendNotificationWriter implements ItemWriter<SendNotificationProcessor.NotificationCompleteData>, StepExecutionListener {
	private static final String PATTERN_DATA_DD_MM_YYYY_HH_MM_SS_SSS = "ddMMyyyyHHmmSSsss";
	private static final String [] MAGGIOLI_JPPA_HEADER_FILE_CSV = {"idDominio","iuv","cpp","esito","warnings","errors"};
	private static final String TIPO_TRACCIATO_DESC = " inviati al servizio Maggioli JPPA";
	private static final String OGGETTO_DEFAULT_MAIL = "Pagamenti{0} al {1}";
	private static final DateTimeFormatter FORMATTER_DATA_ORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Europe/Rome"));

	private static final String P_INVIA_TRACCIATO_ESITO = "INVIA_TRACCIATO_ESITO";
	private static final String P_FILE_SYSTEM_PATH = "FILE_SYSTEM_PATH";
	private static final String P_EMAIL_ALLEGATO = Costanti.P_EMAIL_ALLEGATO;
	private static final String P_EMAIL_INDIRIZZO = Costanti.P_EMAIL_INDIRIZZO;
	private static final String P_EMAIL_SUBJECT = Costanti.P_EMAIL_SUBJECT;

	@Value("#{stepExecutionContext['codDominio']}")
    private String codDominio;

	@Value("#{stepExecutionContext['codConnettore']}")
    private String codConnettore;

	private final JppaConfigRepository jppaConfigRepository;
	private final ConnettoreService connettoreService;
	private final MaggioliMailService mailService;
	private final SimpleDateFormat sdf;
	private final AtomicInteger progressivo = new AtomicInteger(0);
	private final CSVUtils csvUtils = CSVUtils.getInstance(CSVFormat.DEFAULT);

	private ZipOutputStream zos;
	private File zipFile;
	private Instant lastDataMsgRicevuta;
	private int recordCount;
	private boolean inviaTracciatoEsito;
	private boolean allegaZip;
	private List<String> emailDestinatari;
	private String emailOggetto;

    public SendNotificationWriter(JppaConfigRepository jppaConfigRepository, ConnettoreService connettoreService, MaggioliMailService mailService) {
    	this.jppaConfigRepository = jppaConfigRepository;
    	this.connettoreService = connettoreService;
    	this.mailService = mailService;
		this.sdf = new SimpleDateFormat(PATTERN_DATA_DD_MM_YYYY_HH_MM_SS_SSS);
		this.sdf.setTimeZone(TimeZone.getTimeZone("Europe/Rome"));
		this.sdf.setLenient(false);
    }

    private Instant maxData(Instant data1, Instant data2) {
		if (data2 == null)
			return data1;
		if (data1 == null)
			return data2;
		return data1.isAfter(data2) ? data1 : data2;
	}

    @Override
    @Transactional
    public void write(Chunk<? extends SendNotificationProcessor.NotificationCompleteData> chunk) throws IOException {
        for (SendNotificationProcessor.NotificationCompleteData data : chunk) {
        	lastDataMsgRicevuta = maxData(data.getDataMsgRicevuta(), lastDataMsgRicevuta);

        	if (!inviaTracciatoEsito) {
        		continue;
        	}

            log.info("Scrittura record CSV: Dominio={}, Iuv={}", data.getCodDominio(), data.getIuv());

            try {
            	String[] csvData = new String[] {data.getCodDominio(), data.getIuv(), data.getCcp(), data.getEsito(), data.getWarnings(), data.getErrors()};
            	zos.write(csvUtils.toCsv(csvData).getBytes());
            	recordCount++;
                log.info("Aggiunto record CSV: Dominio={}, Iuv={}", data.getCodDominio(), data.getIuv());
            } catch (IOException e) {
                log.error("Errore nella generazione del record CSV: Dominio={}, Iuv={}, Msg={}", data.getCodDominio(), data.getIuv(), e.getMessage(), e);
                throw e;
            }
        }
    }

	@Override
    public void beforeStep(StepExecution stepExecution) {
		Map<String, String> connettoreProps = connettoreService.getConnettoreAsMap(codConnettore);
		log.debug("Configurazione connettore {}: {}", codConnettore, connettoreProps);
		this.inviaTracciatoEsito = ConnettoreMapUtils.getBoolean(connettoreProps, P_INVIA_TRACCIATO_ESITO, false);

		if (!inviaTracciatoEsito) {
			log.info("Produzione tracciato di esito disabilitata per connettore {} dominio {}", codConnettore, codDominio);
			return;
		}

		String destinatariRaw = ConnettoreMapUtils.getString(connettoreProps, P_EMAIL_INDIRIZZO, "");
		this.emailDestinatari = Arrays.stream(destinatariRaw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty()).toList();
		this.emailOggetto = ConnettoreMapUtils.getString(connettoreProps, P_EMAIL_SUBJECT, null);
		this.allegaZip = ConnettoreMapUtils.getBoolean(connettoreProps, P_EMAIL_ALLEGATO, false);

		String fileSystemPath = ConnettoreMapUtils.getString(connettoreProps, P_FILE_SYSTEM_PATH, "/tmp");
		log.info("Produzione tracciato di esito abilitata per connettore {} dominio {}, directory: {}", codConnettore, codDominio, fileSystemPath);

    	try {
	    	File dir = new File(fileSystemPath);
	    	if (!dir.exists()) {
	    		dir.mkdirs();
	    	}
	    	String baseReportName = "GOVPAY_" + codDominio + "_" + sdf.format(new Date());
	    	this.zipFile = new File(dir, baseReportName + "_" + progressivo.addAndGet(1) + ".zip");
	    	OutputStream oututStreamDestinazione = new FileOutputStream(zipFile);
	    	this.zos = new ZipOutputStream(oututStreamDestinazione);
	    	ZipEntry tracciatoOutputEntry = new ZipEntry(baseReportName + "_" + progressivo.addAndGet(1) +".csv");
			zos.putNextEntry(tracciatoOutputEntry);

			zos.write(csvUtils.toCsv(MAGGIOLI_JPPA_HEADER_FILE_CSV).getBytes());

			log.debug("Zip report inizializzato per nuova esecuzione dello step");
    	} catch (IOException e) {
            log.error("Fail to initialize report zip: {}", e.getMessage());
            log.error(e.getMessage(), e);
            throw new RuntimeException("Fail to start step", e);
    	}
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
    	try {
	        if (lastDataMsgRicevuta != null) {
		        // aggiorno ultima data ricevuta notificata
		        JppaConfig jppaConfig = jppaConfigRepository.findByCodDominio(codDominio).orElse(null);
		        jppaConfig.setDataUltimaRt(lastDataMsgRicevuta);
		        jppaConfigRepository.save(jppaConfig);
	        }

	        if (zos != null) {
		        log.debug("Chiusura zip report per fine esecuzione dello step");
		        zos.flush();
				zos.closeEntry();
				zos.flush();
				zos.close();
				zos = null;
	        }
    	} catch (IOException e) {
            log.error("Fail to close report zip: {}", e.getMessage());
            log.error(e.getMessage(), e);
            throw new RuntimeException("Fail to complete step", e);
    	}

    	inviaEmailReport();

        // Leave unchanged exit status
        return null;
    }

    private void inviaEmailReport() {
    	log.debug("inviaEmailReport: inviaTracciatoEsito={}, zipFile={}, allegaZip={}, mailAbilitato={}, destinatari={}",
    			inviaTracciatoEsito, zipFile, allegaZip, mailService.isAbilitato(), emailDestinatari);
    	if (!inviaTracciatoEsito) {
    		return;
    	}
    	if (!mailService.isAbilitato()) {
    		log.warn("INVIA_TRACCIATO_ESITO=true ma il servizio mail non è configurato o non è abilitato");
    		return;
    	}
    	if (emailDestinatari == null || emailDestinatari.isEmpty()) {
    		log.warn("INVIA_TRACCIATO_ESITO=true ma nessun destinatario configurato (EMAIL_INDIRIZZO) per connettore {} dominio {}", codConnettore, codDominio);
    		return;
    	}

    	try {
    		MailInfo.MailInfoBuilder builder = MailInfo.builder()
    				.to(List.of(emailDestinatari.get(0)))
    				.oggetto(buildMailOggetto())
    				.testo(buildMailBody());

    		if (emailDestinatari.size() > 1) {
    			builder.cc(emailDestinatari.subList(1, emailDestinatari.size()));
    		}

    		if (allegaZip && zipFile != null && zipFile.exists()) {
    			byte[] zipBytes = Files.readAllBytes(zipFile.toPath());
    			log.debug("Invio email report: destinatari={}, oggetto={}, allegato={} ({} bytes)",
    					emailDestinatari, emailOggetto, zipFile.getName(), zipBytes.length);
    			builder.allegati(Map.of(zipFile.getName(), zipBytes));
    		} else {
    			log.debug("Invio email report: destinatari={}, oggetto={} (senza allegato)", emailDestinatari, emailOggetto);
    		}

    		mailService.inviaEmail(builder.build());
    		log.info("Email report inviata a {} per connettore {} dominio {}", emailDestinatari, codConnettore, codDominio);
    	} catch (Exception e) {
    		log.error("Errore nell'invio email report per connettore {} dominio {}: {}", codConnettore, codDominio, e.getMessage(), e);
    	}
    }

    private String buildMailOggetto() {
        if (emailOggetto != null && !emailOggetto.isEmpty()) {
            return emailOggetto;
        }
        String dataFine = lastDataMsgRicevuta != null ? FORMATTER_DATA_ORA.format(lastDataMsgRicevuta) : "";
        return MessageFormat.format(OGGETTO_DEFAULT_MAIL, TIPO_TRACCIATO_DESC, dataFine);
    }

    private String buildMailBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("Salve,");
        sb.append("\n");

        if (allegaZip) {
            sb.append("\nin allegato alla presente il tracciato dei pagamenti").append(TIPO_TRACCIATO_DESC).append(":");
        } else {
            sb.append("\ndi seguito le informazioni sul tracciato dei pagamenti").append(TIPO_TRACCIATO_DESC).append(":");
        }

        sb.append("\n");
        sb.append("\nEnte Creditore: ").append(codDominio);
        if (lastDataMsgRicevuta != null) {
            sb.append("\nData ultima ricevuta: ").append(FORMATTER_DATA_ORA.format(lastDataMsgRicevuta));
        }
        sb.append("\nNumero pagamenti: ").append(recordCount);
        sb.append("\n");
        sb.append("\nLa seguente comunicazione proviene da un sistema automatico.");
        sb.append("\n");
        sb.append("\nCordiali saluti.");

        return sb.toString();
    }
}
