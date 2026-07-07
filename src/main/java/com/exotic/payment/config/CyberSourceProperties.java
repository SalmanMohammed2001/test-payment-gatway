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
        String defaultCurrency
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
            defaultCurrency = "USD";
        }
    }
}
