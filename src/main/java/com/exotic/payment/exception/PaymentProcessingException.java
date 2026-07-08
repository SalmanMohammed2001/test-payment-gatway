package com.exotic.payment.exception;

import com.exotic.payment.dto.ProviderFieldError;

import java.util.List;

/**
 * Raised when a call to the CyberSource API fails or returns an error status.
 *
 * <p>When CyberSource reports field-level problems (e.g. reason code 101 -
 * MISSING_FIELD / INVALID_REQUEST) the offending fields are exposed via
 * {@link #getFieldErrors()}.
 */
public class PaymentProcessingException extends RuntimeException {

    private final int providerHttpStatus;
    private final String providerBody;
    private final String providerReason;
    private final transient List<ProviderFieldError> fieldErrors;

    public PaymentProcessingException(String message,
                                      int providerHttpStatus,
                                      String providerBody,
                                      String providerReason,
                                      List<ProviderFieldError> fieldErrors,
                                      Throwable cause) {
        super(message, cause);
        this.providerHttpStatus = providerHttpStatus;
        this.providerBody = providerBody;
        this.providerReason = providerReason;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public PaymentProcessingException(String message, int providerHttpStatus, String providerBody, Throwable cause) {
        this(message, providerHttpStatus, providerBody, null, List.of(), cause);
    }

    public PaymentProcessingException(String message, Throwable cause) {
        this(message, 0, null, null, List.of(), cause);
    }

    public PaymentProcessingException(String message) {
        this(message, 0, null, null, List.of(), null);
    }

    public int getProviderHttpStatus() {
        return providerHttpStatus;
    }

    public String getProviderBody() {
        return providerBody;
    }

    public String getProviderReason() {
        return providerReason;
    }

    public List<ProviderFieldError> getFieldErrors() {
        return fieldErrors;
    }
}
