package com.exotic.payment.config;

import Invokers.ApiClient;
import com.cybersource.authsdk.core.MerchantConfig;
import com.exotic.payment.exception.PaymentProcessingException;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Produces configured CyberSource {@link ApiClient} instances.
 *
 * <p>The immutable merchant {@link Properties} are prepared once; a fresh
 * {@link ApiClient} is created per request since the SDK client is stateful.
 */
@Component
public class CyberSourceClientFactory {

    private final Properties merchantProperties;

    public CyberSourceClientFactory(CyberSourceProperties properties) {
        this.merchantProperties = buildProperties(properties);
    }

    private Properties buildProperties(CyberSourceProperties props) {
        Properties p = new Properties();
        p.setProperty("authenticationType", props.authenticationType());
        p.setProperty("merchantID", nullSafe(props.merchantId()));
        p.setProperty("merchantKeyId", nullSafe(props.merchantKeyId()));
        p.setProperty("merchantsecretKey", nullSafe(props.merchantSecretKey()));
        p.setProperty("runEnvironment", props.runEnvironment());
        p.setProperty("enableLog", Boolean.toString(props.enableLog()));
        return p;
    }

    /**
     * Builds a new {@link ApiClient} backed by the merchant configuration.
     */
    public ApiClient newApiClient() {
        try {
            MerchantConfig merchantConfig = new MerchantConfig(merchantProperties);
            return new ApiClient(merchantConfig);
        } catch (Exception e) {
            throw new PaymentProcessingException(
                    "Failed to initialise CyberSource client: " + e.getMessage(), 0, null, e);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
