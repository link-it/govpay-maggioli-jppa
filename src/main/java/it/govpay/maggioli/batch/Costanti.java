package it.govpay.maggioli.batch;

public class Costanti {
	public static final int RPT_ESITO_PAGAMENTO_ESEGUITO = 0;
	public static final int RPT_ESITO_PAGAMENTO_PARZIALMENTE_ESEGUITO = 2;

	private Costanti() {
		// Costruttore privato per evitare istanziazione
	}

	// Nome job Maggioli JPPA notification
	public static final String MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME = "maggioliJppaNotificationJob";

	// Esiti notifica
	public static final String ESITO_ERRORE_INVIO = "ERRORE_INVIO";

	// Parametri connettore - email report
	public static final String P_EMAIL_ALLEGATO = "EMAIL_ALLEGATO";
	public static final String P_EMAIL_INDIRIZZO = "EMAIL_INDIRIZZO";
	public static final String P_EMAIL_SUBJECT = "EMAIL_SUBJECT";
	public static final String DEFAULT_EMAIL_OGGETTO = "Report notifiche pagamento JPPA";
	public static final String DEFAULT_EMAIL_TESTO = "In allegato il report delle notifiche di pagamento.";

	// GDE - Nomi operazioni
	public static final String OPERATION_LOGIN = "loginUsingPOST";
	public static final String OPERATION_NOTIFICA_PAGAMENTO = "postPagamentiV2UsingPOST";

	// GDE - Path API
	public static final String PATH_LOGIN = "/rest/login";
	public static final String PATH_NOTIFICA_PAGAMENTO = "/rest/notifiche/v2/pagamenti";
}
