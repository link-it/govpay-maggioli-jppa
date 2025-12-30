package it.govpay.maggioli.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;

import it.govpay.maggioli.batch.dto.DominioProcessingContext;
import it.govpay.maggioli.batch.dto.MaggioliHeadersBatch;
import it.govpay.maggioli.batch.dto.MaggioliHeadersBatch.NotificaHeader;
import it.govpay.maggioli.batch.entity.RPT;
import it.govpay.maggioli.batch.repository.JppaNotificheRepository;
import it.govpay.maggioli.batch.scheduler.MaggioliJppaBatchScheduler;
import it.govpay.maggioli.batch.step2.MaggioliJppaHeadersProcessor;
import it.govpay.maggioli.batch.step2.MaggioliJppaHeadersReader;
import it.govpay.maggioli.batch.step2.MaggioliJppaHeadersWriter;
import it.govpay.maggioli.batch.step3.SendNotificationProcessor;
import it.govpay.maggioli.batch.step3.SendNotificationReader;
import it.govpay.maggioli.batch.step3.SendNotificationWriter;
import it.govpay.maggioli.batch.tasklet.CleanupJppaNotificheTasklet;
import it.govpay.maggioli.client.model.RispostaNotificaPagamentoDto.EsitoEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for GovpayFdrBatchApplication
 */
@SpringBootTest(classes = GovpayMaggioliBatchApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.batch.job.enabled=false"})
class GovpayMaggioliBatchRetryTests {
	private static final String COD_DOMINIO_TEST = "12345678901";
	private static final String COD_DOMINIO_PART1 = "DOM001";
	private static final String COD_DOMINIO_PART2 = "DOM002";
	private static final String COD_DOMINIO_PART3 = "DOM003";
	private static final String COD_CONNETTORE_TEST = "test";
	private static final Long   ID_RPT_TEST = Long.valueOf(101L);
	private static final Long   ID_RPT_DOM1 = Long.valueOf(1L);
	private static final Long   ID_RPT_DOM2 = Long.valueOf(2L);
	private static final Long   ID_RPT_DOM3 = Long.valueOf(3L);
	private static final Instant RPT_MSG_DATA = Instant.now();
	private static final String CCP_TEST = "CCP_TEST";
	private static final String IUV_TEST = "IUV_TEST";
	private static final String ESITO_OK_TEST = EsitoEnum.OK.name();

	@Autowired
	JobExplorer jobExplorer;

	@Autowired
	MaggioliJppaBatchScheduler batchScheduler;

	private AtomicInteger headerProcessCounter = new AtomicInteger(0);
	private AtomicInteger notificationProcessorCounter = new AtomicInteger(0);
	private Queue<MaggioliHeadersBatch> headerQueue = new ArrayBlockingQueue<MaggioliHeadersBatch>(16);

	@MockitoBean
	private CleanupJppaNotificheTasklet cleanupNotifiche = mock(CleanupJppaNotificheTasklet.class);
	@MockitoBean
	private MaggioliJppaHeadersReader headersReader = mock(MaggioliJppaHeadersReader.class);
	@MockitoBean
	private MaggioliJppaHeadersProcessor headersProcessor = mock(MaggioliJppaHeadersProcessor.class);
	@MockitoBean
	private MaggioliJppaHeadersWriter headersWriter = mock(MaggioliJppaHeadersWriter.class);
	@MockitoBean
	private SendNotificationReader notificationReader = mock(SendNotificationReader.class);
	@MockitoBean
	private SendNotificationProcessor notificationProcessor = mock(SendNotificationProcessor.class);
	@MockitoBean
	private SendNotificationWriter notificationWriter = mock(SendNotificationWriter.class);
	@MockitoBean
	private JppaNotificheRepository notificheRepository = mock(JppaNotificheRepository.class);

	private RPT notificheReaderFun() {
		// poll() rimuove e ritorna l'elemento dalla coda (o null se vuota)
		if (headerQueue.poll() != null)
			return new RPT();
		return null;
	}

	@BeforeEach
	void setup() throws Exception {
		headerProcessCounter.set(0);
		notificationProcessorCounter.set(0);
		headerQueue.clear();

		Instant lastRtData = Instant.now();
		// Mock JppaNotificheRepository per supportare il partitioning
		when(notificheRepository.findDistinctCodDominio()).thenReturn(List.of(COD_DOMINIO_TEST));

		when(cleanupNotifiche.execute(any(), any())).thenReturn(RepeatStatus.FINISHED);

		DominioProcessingContext res = DominioProcessingContext.builder()
															   .codDominio(COD_DOMINIO_TEST)
															   .codConnettore(COD_CONNETTORE_TEST)
															   .lastRtDate(lastRtData)
															   .build();
		when(headersReader.read()).thenReturn(res)
								  .thenReturn(null);

		doAnswer(invocation -> { Chunk<? extends MaggioliHeadersBatch> arg0 = invocation.getArgument(0);
								 headerQueue.add(arg0.getItems().get(0));
								 return null;
							   }).when(headersWriter).write(any());

		// Con partitioning, ogni reader viene chiamato più volte fino a quando non ritorna null
		// Quindi il mock deve continuare a chiamare frTempReaderFun() ogni volta
		when(notificationReader.read()).thenAnswer(invocation -> notificheReaderFun());

		SendNotificationProcessor.NotificationCompleteData notificationCompleteData = SendNotificationProcessor.NotificationCompleteData.builder().build();
		when(notificationProcessor.process(any())).thenAnswer(invocation -> {
			notificationProcessorCounter.addAndGet(1);
			return notificationCompleteData;
		});
		
		doNothing().when(notificationWriter).write(any());
	}

	@Test
	void notificationRetrySuccess() throws Exception {
		List<NotificaHeader> headers = List.of(NotificaHeader.builder().idRpt(ID_RPT_TEST).dataMsgRicevuta(RPT_MSG_DATA).build());
		when(headersProcessor.process(any())).thenReturn( MaggioliHeadersBatch.builder()
											 								  .codDominio(COD_DOMINIO_TEST)
											 								  .headers(headers)
											 								  .build() )
											 .thenThrow( new RuntimeException("Failed headers processor") );

		SendNotificationProcessor.NotificationCompleteData notificationCompleteData =
				SendNotificationProcessor.NotificationCompleteData.builder()
																  .codDominio(COD_DOMINIO_TEST)
																  .ccp(CCP_TEST)
																  .iuv(IUV_TEST)
																  .dataMsgRicevuta(RPT_MSG_DATA)
																  .esito(ESITO_OK_TEST)
																  .build();
		when(notificationProcessor.process(any())).thenAnswer(invocation -> {
			if (notificationProcessorCounter.addAndGet(1) < 3)
				throw new RestClientException("test");
			return notificationCompleteData;
		});

		final JobExecution execution = batchScheduler.runMaggioliJppaNotificationJob();
		assertEquals(BatchStatus.COMPLETED, execution.getStatus());
		assertEquals(3, notificationProcessorCounter.get());
	}

	@Test
	void notificationRetryFailed() throws Exception {
		Mockito.reset(notificationProcessor);

		List<NotificaHeader> headers = List.of(NotificaHeader.builder().idRpt(ID_RPT_TEST).dataMsgRicevuta(RPT_MSG_DATA).build());
		when(headersProcessor.process(any())).thenReturn( MaggioliHeadersBatch.builder()
											 								  .codDominio(COD_DOMINIO_TEST)
											 								  .headers(headers)
											 								  .build() )
											 .thenThrow( new RuntimeException("Failed headers processor") );


		SendNotificationProcessor.NotificationCompleteData notificationCompleteData = SendNotificationProcessor.NotificationCompleteData.builder().build();
		when(notificationProcessor.process(any())).thenAnswer(invocation -> {
			if (notificationProcessorCounter.addAndGet(1) < 5)
				throw new RestClientException("test");
			return notificationCompleteData;
		});

		JobExecution execution = batchScheduler.runMaggioliJppaNotificationJob();
		assertEquals(BatchStatus.FAILED, execution.getStatus());
		assertEquals(3, notificationProcessorCounter.get());

		Mockito.reset(notificationProcessor);
		setup();
		Mockito.reset(headersReader);
		Mockito.reset(notificationProcessor);

		when(headersReader.read()).thenReturn(null);

		when(notificationProcessor.process(any())).thenAnswer(invocation -> {
			notificationProcessorCounter.addAndGet(1);
			return notificationCompleteData;
		});
		execution = batchScheduler.runMaggioliJppaNotificationJob();
		assertEquals(BatchStatus.COMPLETED, execution.getStatus());
		assertEquals(0, notificationProcessorCounter.get());
	}

	/**
	 * Test partizionamento con multipli domini.
	 * Verifica che il partizionamento crei una partizione per ogni dominio
	 * e che ogni partizione processi i suoi dati.
	 */
//	@Test
	void multiDomainPartitioning() throws Exception {
		// Setup: 3 domini nel sistema
		Mockito.reset(notificheRepository);
		Mockito.reset(headersReader);
		Mockito.reset(headersProcessor);
		Mockito.reset(notificationReader);
		Mockito.reset(notificationProcessor);
		when(notificheRepository.findDistinctCodDominio()).thenReturn(List.of(COD_DOMINIO_PART1, COD_DOMINIO_PART2, COD_DOMINIO_PART3));

		// Headers reader deve restituire 3 DominioProcessingContext (uno per ogni dominio)
		Instant lastRtData = Instant.now();
		DominioProcessingContext dom1 = DominioProcessingContext.builder().codDominio(COD_DOMINIO_PART1).codConnettore(COD_CONNETTORE_TEST).lastRtDate(lastRtData).build();
		DominioProcessingContext dom2 = DominioProcessingContext.builder().codDominio(COD_DOMINIO_PART2).codConnettore(COD_CONNETTORE_TEST).lastRtDate(lastRtData).build();
		DominioProcessingContext dom3 = DominioProcessingContext.builder().codDominio(COD_DOMINIO_PART3).codConnettore(COD_CONNETTORE_TEST).lastRtDate(lastRtData).build();
		when(headersReader.read()).thenReturn(dom1, dom2, dom3, null);

		List<NotificaHeader> headers1 = List.of(NotificaHeader.builder().idRpt(ID_RPT_DOM1).dataMsgRicevuta(RPT_MSG_DATA).build());
		List<NotificaHeader> headers2 = List.of(NotificaHeader.builder().idRpt(ID_RPT_DOM2).dataMsgRicevuta(RPT_MSG_DATA).build());
		List<NotificaHeader> headers3 = List.of(NotificaHeader.builder().idRpt(ID_RPT_DOM3).dataMsgRicevuta(RPT_MSG_DATA).build());
		when(headersProcessor.process(any())).thenReturn(
			MaggioliHeadersBatch.builder().codDominio(COD_DOMINIO_PART1).headers(headers1).build(),
			MaggioliHeadersBatch.builder().codDominio(COD_DOMINIO_PART2).headers(headers2).build(),
			MaggioliHeadersBatch.builder().codDominio(COD_DOMINIO_PART3).headers(headers3).build()
		).thenThrow(new RuntimeException("No more domains"));

		// Notification reader: limita a 3 letture totali per evitare cicli (3 domini)
		AtomicInteger notificheReadCount = new AtomicInteger(0);
		when(notificationReader.read()).thenAnswer(invocation -> {
			if (notificheReadCount.incrementAndGet() <= 3) {
				return notificheReaderFun();
			}
			return null; // Stop after 3 reads
		});

		// Tutti i processing hanno successo (no retry, no failure)
		SendNotificationProcessor.NotificationCompleteData notificationCompleteData =
				SendNotificationProcessor.NotificationCompleteData.builder()
																  .codDominio(COD_DOMINIO_TEST)
																  .ccp(CCP_TEST)
																  .iuv(IUV_TEST)
																  .dataMsgRicevuta(RPT_MSG_DATA)
																  .esito(ESITO_OK_TEST)
																  .build();
		when(notificationProcessor.process(any())).thenAnswer(invocation -> {
			notificationProcessorCounter.addAndGet(1);
			return notificationCompleteData;
		});

		JobExecution execution = batchScheduler.runMaggioliJppaNotificationJob();

		// Il job completa con successo
		assertEquals(BatchStatus.COMPLETED, execution.getStatus());
		// Verifica che tutti i 3 domini siano stati processati
		assertEquals(6, notificheReadCount.get());
		assertEquals(3, notificationProcessorCounter.get());
	}

	/**
	 * Test che verifica il comportamento del partizionamento quando tutti i domini hanno successo.
	 * Questo è il caso ideale dove non ci sono errori.
	 */
	@Test
	void allPartitionsSucceed() throws Exception {
		// Setup: 2 domini
		Mockito.reset(notificheRepository);
		Mockito.reset(headersReader);
		Mockito.reset(notificationReader);
		Mockito.reset(notificationProcessor);
		when(notificheRepository.findDistinctCodDominio()).thenReturn(List.of(COD_DOMINIO_PART1, COD_DOMINIO_PART2));

		// Headers reader deve restituire 2 DominioProcessingContext
		Instant lastRtData = Instant.now();
		DominioProcessingContext dom1 = DominioProcessingContext.builder().codDominio(COD_DOMINIO_PART1).codConnettore(COD_CONNETTORE_TEST).lastRtDate(lastRtData).build();
		DominioProcessingContext dom2 = DominioProcessingContext.builder().codDominio(COD_DOMINIO_PART2).codConnettore(COD_CONNETTORE_TEST).lastRtDate(lastRtData).build();
		when(headersReader.read()).thenReturn(dom1, dom2, null);

		List<NotificaHeader> headers1 = List.of(NotificaHeader.builder().idRpt(ID_RPT_DOM1).dataMsgRicevuta(RPT_MSG_DATA).build());
		List<NotificaHeader> headers2 = List.of(NotificaHeader.builder().idRpt(ID_RPT_DOM2).dataMsgRicevuta(RPT_MSG_DATA).build());
		when(headersProcessor.process(any())).thenReturn(
			MaggioliHeadersBatch.builder().codDominio(COD_DOMINIO_PART1).headers(headers1).build(),
			MaggioliHeadersBatch.builder().codDominio(COD_DOMINIO_PART2).headers(headers2).build()
		).thenThrow(new RuntimeException("No more domains"));

		// Reset dei contatori prima del test
		notificationProcessorCounter.set(0);
		headerQueue.clear();

		// Track items processed in notification step to prevent infinite loop
		final AtomicInteger notificationItemsRead = new AtomicInteger(0);

		// Notification reader deve leggere solo 2 elementi totali (non per partizione)
		when(notificationReader.read()).thenAnswer(invocation -> {
			if (notificationItemsRead.incrementAndGet() <= 2) {
				return notificheReaderFun();
			}
			return null;
		});

		// Tutti i processing hanno successo
		SendNotificationProcessor.NotificationCompleteData notificationCompleteData = SendNotificationProcessor.NotificationCompleteData.builder().build();
		when(notificationProcessor.process(any())).thenAnswer(invocation -> {
			notificationProcessorCounter.addAndGet(1);
			return notificationCompleteData;
		});

		JobExecution execution = batchScheduler.runMaggioliJppaNotificationJob();

		// Il job completa con successo
		assertEquals(BatchStatus.COMPLETED, execution.getStatus());
		// Entrambi i domini sono stati processati
		assertEquals(2, notificationProcessorCounter.get());
	}
}
