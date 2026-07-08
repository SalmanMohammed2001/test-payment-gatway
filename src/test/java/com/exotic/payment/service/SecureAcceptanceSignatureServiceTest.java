package com.exotic.payment.service;

import com.exotic.payment.config.SecureAcceptanceProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecureAcceptanceSignatureServiceTest {

    private static final String SECRET =
            "ea509fee044f474a91343ede6f0829ad51526587bf794a0c81dc6e154a72580d";

    private final SecureAcceptanceSignatureService service =
            new SecureAcceptanceSignatureService(props(SECRET));

    private static SecureAcceptanceProperties props(String secret) {
        return new SecureAcceptanceProperties("profile", "access", secret, null, null, null, null, null);
    }

    private Map<String, String> sampleFields() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("access_key", "access");
        f.put("profile_id", "profile");
        f.put("transaction_uuid", "abc-123");
        f.put("signed_field_names",
                "access_key,profile_id,transaction_uuid,signed_field_names,amount,currency");
        f.put("amount", "102.21");
        f.put("currency", "LKR");
        return f;
    }

    @Test
    void signThenVerifyRoundTrips() {
        Map<String, String> fields = sampleFields();
        fields.put("signature", service.sign(fields));

        assertThat(service.verify(fields)).isTrue();
    }

    @Test
    void signatureIsDeterministicForSameInput() {
        assertThat(service.sign(sampleFields())).isEqualTo(service.sign(sampleFields()));
    }

    @Test
    void tamperedFieldFailsVerification() {
        Map<String, String> fields = sampleFields();
        fields.put("signature", service.sign(fields));

        fields.put("amount", "1.00"); // attacker lowers the amount after signing

        assertThat(service.verify(fields)).isFalse();
    }

    @Test
    void missingSignatureFailsVerification() {
        assertThat(service.verify(sampleFields())).isFalse();
    }

    @Test
    void wrongSecretFailsVerification() {
        Map<String, String> fields = sampleFields();
        fields.put("signature", service.sign(fields));

        SecureAcceptanceSignatureService other =
                new SecureAcceptanceSignatureService(props("a-different-secret-key"));

        assertThat(other.verify(fields)).isFalse();
    }
}
