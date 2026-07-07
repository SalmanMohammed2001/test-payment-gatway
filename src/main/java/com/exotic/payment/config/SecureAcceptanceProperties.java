package com.exotic.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code secure-acceptance.*} settings used by the CyberSource
 * Secure Acceptance (Hosted Checkout) integration.
 *
 * <p>These come from a Secure Acceptance profile in the Business Center and are
 * entirely separate from the REST {@link CyberSourceProperties}. The
 * {@code secretKey} is a signing credential and must never be logged.
 */
@ConfigurationProperties(prefix = "secure-acceptance")
public record SecureAcceptanceProperties(
        String profileId,
        String accessKey,
        String secretKey,
        String payUrl,
        String frontendResultUrl,
        String locale,
        String defaultCurrency
) {
    public SecureAcceptanceProperties {
        if (payUrl == null || payUrl.isBlank()) {
            payUrl = "https://testsecureacceptance.cybersource.com/pay";
        }
        if (frontendResultUrl == null || frontendResultUrl.isBlank()) {
            frontendResultUrl = "http://localhost:4200/checkout/result";
        }
        if (locale == null || locale.isBlank()) {
            locale = "en";
        }
        if (defaultCurrency == null || defaultCurrency.isBlank()) {
            defaultCurrency = "USD";
        }
    }
}
