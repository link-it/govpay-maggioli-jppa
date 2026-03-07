package it.govpay.maggioli.batch.exception;

/**
 * Eccezione lanciata quando il login sull'API Maggioli fallisce.
 * Estende RuntimeException (non RestClientException) per evitare che venga
 * catturata dai catch generici di RestClientException e per impedire il retry
 * automatico di Spring Batch.
 */
public class LoginFailedException extends RuntimeException {

    public LoginFailedException(String message) {
        super(message);
    }

    public LoginFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
