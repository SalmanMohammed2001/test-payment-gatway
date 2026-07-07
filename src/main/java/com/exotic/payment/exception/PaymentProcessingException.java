package com.exotic.payment.exception;

/**
 * Raised when a call to the CyberSource API fails or returns an error status.
 */
public class PaymentProcessingException extends RuntimeException {

    private final int providerHttpStatus;
    private final String providerBody;

    public PaymentProcessingException(String message, int providerHttpStatus, String providerBody, Throwable cause) {
        super(message, cause);
        this.providerHttpStatus = providerHttpStatus;
        this.providerBody = providerBody;
    }

    public PaymentProcessingException(String message) {
        this(message, 0, null, null);
    }

    public PaymentProcessingException(String message, Throwable cause) {
        this(message, 0, null, cause);
    }

    public int getProviderHttpStatus() {
        return providerHttpStatus;
    }

    public String getProviderBody() {
        return providerBody;
    }
}
