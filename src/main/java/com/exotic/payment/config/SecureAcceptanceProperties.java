package com.exotic.payment.config;

import com.exotic.payment.dto.CheckoutBillingDto;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code secure-acceptance.*} settings used by the CyberSource
 * Secure Acceptance (Hosted Checkout) integration.
 */
@ConfigurationProperties(prefix = "secure-acceptance")
public record SecureAcceptanceProperties(
        String profileId,
        String accessKey,
        String secretKey,
        String payUrl,
        String frontendResultUrl,
        String locale,
        String defaultCurrency,
        CheckoutBillingDto defaultBilling
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
            defaultCurrency = "LKR";
        }
    }

    /** Fallback billing used when the checkout request omits {@code billTo}. */
    public CheckoutBillingDto resolvedDefaultBilling() {
        if (defaultBilling == null) {
            return new CheckoutBillingDto(
                    "John", "Doe", "test@cybs.com",
                    "1 Market St", "Suite 100", "San Francisco", "CA",
                    "94105", "US", "4158880000");
        }
        return defaultBilling;
    }
}
