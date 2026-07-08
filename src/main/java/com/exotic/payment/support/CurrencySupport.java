package com.exotic.payment.support;

import com.exotic.payment.exception.PaymentProcessingException;

/** Single supported settlement currency for this integration. */
public final class CurrencySupport {

    public static final String LKR = "LKR";

    private CurrencySupport() {
    }

    /**
     * Returns {@link #LKR}. Blank/null is treated as LKR; any other code is rejected.
     */
    public static String resolve(String currency) {
        if (currency == null || currency.isBlank()) {
            return LKR;
        }
        if (!LKR.equalsIgnoreCase(currency.trim())) {
            throw new PaymentProcessingException(
                    "Only LKR currency is supported. Requested: " + currency);
        }
        return LKR;
    }
}
