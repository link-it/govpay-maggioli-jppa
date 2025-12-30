package it.govpay.maggioli.batch;

import org.springframework.http.MediaType;

public class Costanti {
	public static final int RPT_ESITO_PAGAMENTO_ESEGUITO = 0;
	public static final int RPT_ESITO_PAGAMENTO_PARZIALMENTE_ESEGUITO = 2;

	private Costanti() {
		// Costruttore privato per evitare istanziazione
	}
	
	// Job parameters per gestione multi-nodo
	public static final String GOVPAY_BATCH_JOB_ID = "JobID";
	public static final String GOVPAY_BATCH_JOB_PARAMETER_WHEN = "When";
	public static final String GOVPAY_BATCH_JOB_PARAMETER_CLUSTER_ID = "ClusterID";
	
	public static final String HEADER_X_REQUEST_ID = "X-Request-Id";
	public static final String GOVPAY_GDE_HEADER_ACCEPT = MediaType.APPLICATION_JSON_VALUE;
	public static final String GOVPAY_GDE_HEADER_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;

	// Nome job Maggioli JPPA notification
	public static final String MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME = "maggioliJppaNotificationJob";
}
