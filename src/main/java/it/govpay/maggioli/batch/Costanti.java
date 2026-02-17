package it.govpay.maggioli.batch;

public class Costanti {
	public static final int RPT_ESITO_PAGAMENTO_ESEGUITO = 0;
	public static final int RPT_ESITO_PAGAMENTO_PARZIALMENTE_ESEGUITO = 2;

	private Costanti() {
		// Costruttore privato per evitare istanziazione
	}

	// Nome job Maggioli JPPA notification
	public static final String MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME = "maggioliJppaNotificationJob";
}
