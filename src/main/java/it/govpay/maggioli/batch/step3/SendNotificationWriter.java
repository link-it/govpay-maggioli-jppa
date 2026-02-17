package it.govpay.maggioli.batch.step3;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
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
import it.govpay.common.utils.ConnettoreMapUtils;
import it.govpay.maggioli.batch.entity.JppaConfig;
import it.govpay.maggioli.batch.repository.JppaConfigRepository;
import it.govpay.maggioli.batch.utils.CSVUtils;

/**
 * Writer to save report complete data
 */
@Component
@StepScope
@Slf4j
public class SendNotificationWriter implements ItemWriter<SendNotificationProcessor.NotificationCompleteData>, StepExecutionListener {
	private static final String PATTERN_DATA_DD_MM_YYYY_HH_MM_SS_SSS = "ddMMyyyyHHmmSSsss";
	private static final String [] MAGGIOLI_JPPA_HEADER_FILE_CSV = {"idDominio","iuv","cpp","esito","warnings","errors"};

	private static final String P_INVIA_TRACCIATO_ESITO = "INVIA_TRACCIATO_ESITO";
	private static final String P_FILE_SYSTEM_PATH = "FILE_SYSTEM_PATH";

	@Value("#{stepExecutionContext['codDominio']}")
    private String codDominio;

	@Value("#{stepExecutionContext['codConnettore']}")
    private String codConnettore;

	private final JppaConfigRepository jppaConfigRepository;
	private final ConnettoreService connettoreService;
	private final SimpleDateFormat sdf;
	private final AtomicInteger progressivo = new AtomicInteger(0);
	private final CSVUtils csvUtils = CSVUtils.getInstance(CSVFormat.DEFAULT);

	private ZipOutputStream zos;
	private Instant lastDataMsgRicevuta;
	private boolean inviaTracciatoEsito;

    public SendNotificationWriter(JppaConfigRepository jppaConfigRepository, ConnettoreService connettoreService) {
    	this.jppaConfigRepository = jppaConfigRepository;
    	this.connettoreService = connettoreService;
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
		this.inviaTracciatoEsito = ConnettoreMapUtils.getBoolean(connettoreProps, P_INVIA_TRACCIATO_ESITO, false);

		if (!inviaTracciatoEsito) {
			log.info("Produzione tracciato di esito disabilitata per connettore {} dominio {}", codConnettore, codDominio);
			return;
		}

		String fileSystemPath = ConnettoreMapUtils.getString(connettoreProps, P_FILE_SYSTEM_PATH, "/tmp");
		log.info("Produzione tracciato di esito abilitata per connettore {} dominio {}, directory: {}", codConnettore, codDominio, fileSystemPath);

    	try {
	    	String baseReportName = "GOVPAY_" + codDominio + "_" + sdf.format(new Date());
	    	OutputStream oututStreamDestinazione = new FileOutputStream(new File(fileSystemPath, baseReportName + "_" + progressivo.addAndGet(1) +".zip"));
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
        // Leave unchanged exit status
        return null;
    }
}
