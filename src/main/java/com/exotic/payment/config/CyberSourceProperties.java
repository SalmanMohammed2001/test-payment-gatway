package com.exotic.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code cybersource.*} settings from application configuration.
 * These map to the property keys expected by the CyberSource {@code MerchantConfig}.
 */
@ConfigurationProperties(prefix = "cybersource")
public record CyberSourceProperties(
        String authenticationType,
        String merchantId,
        String merchantKeyId,
        String merchantSecretKey,
        String runEnvironment,
        boolean enableLog,
        String defaultCurrency,
        Retry retry
) {
    public CyberSourceProperties {
        if (authenticationType == null || authenticationType.isBlank()) {
            authenticationType = "http_signature";
        }
        if (runEnvironment == null || runEnvironment.isBlank()) {
            // Sandbox host. Use api.cybersource.com for production.
            runEnvironment = "apitest.cybersource.com";
        }
        if (defaultCurrency == null || defaultCurrency.isBlank()) {
            defaultCurrency = "LKR";
        }
        if (retry == null) {
            retry = new Retry(null, null, null);
        }
    }

    /**
     * Bounded-retry policy for CyberSource system errors. Per CyberSource
     * guidance, a SYSTEM_ERROR may be a genuine outage or a processor rejection
     * of invalid data, so retries MUST be bounded — never endless.
     *
     * @param maxAttempts    total attempts including the first (e.g. 3 = 1 try + 2 retries)
     * @param initialBackoff first backoff wait in milliseconds
     * @param maxBackoff     upper bound for the (exponential) backoff in milliseconds
     */
    public record Retry(Integer maxAttempts, Long initialBackoff, Long maxBackoff) {
        public Retry {
            if (maxAttempts == null || maxAttempts < 1) {
                maxAttempts = 3;
            }
            if (initialBackoff == null || initialBackoff < 0) {
                initialBackoff = 500L;
            }
            if (maxBackoff == null || maxBackoff < initialBackoff) {
                maxBackoff = 4000L;
            }
        }
    }
}
