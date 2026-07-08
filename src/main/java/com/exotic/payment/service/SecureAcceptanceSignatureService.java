package com.exotic.payment.service;

import com.exotic.payment.config.SecureAcceptanceProperties;
import com.exotic.payment.exception.PaymentProcessingException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Builds and verifies the HMAC-SHA256 signatures used by CyberSource Secure
 * Acceptance.
 *
 * <p>The data to sign is the comma-joined list of {@code name=value} pairs, one
 * per entry in the {@code signed_field_names} field, in the exact order that
 * field lists them. The signature is the Base64-encoded HMAC-SHA256 of that
 * string, keyed with the profile secret key.
 */
@Service
public class SecureAcceptanceSignatureService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecureAcceptanceProperties properties;

    public SecureAcceptanceSignatureService(SecureAcceptanceProperties properties) {
        this.properties = properties;
    }

    /**
     * Builds the signing key on demand. Validation happens here — not in the
     * constructor — so the application still boots when only the REST flow is
     * configured; a missing secret only fails calls that actually sign/verify.
     */
    private SecretKeySpec signingKey() {
        String secret = properties.secretKey();
        if (secret == null || secret.isBlank()) {
            throw new PaymentProcessingException(
                    "secure-acceptance.secret-key is not configured (set SA_SECRET_KEY)");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    /**
     * Computes the signature for an outgoing request and returns it. The caller
     * is expected to place the result into the {@code signature} field.
     */
    public String sign(Map<String, String> fields) {
        return mac(buildDataToSign(fields));
    }

    /**
     * Verifies an inbound response by recomputing the signature over the fields
     * named in the response's own {@code signed_field_names} and comparing it,
     * in constant time, against the supplied {@code signature}.
     */
    public boolean verify(Map<String, String> fields) {
        String provided = fields.get("signature");
        if (provided == null || provided.isBlank()) {
            return false;
        }
        String expected = mac(buildDataToSign(fields));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private String buildDataToSign(Map<String, String> fields) {
        String signedFieldNames = fields.get("signed_field_names");
        if (signedFieldNames == null || signedFieldNames.isBlank()) {
            throw new PaymentProcessingException("Missing signed_field_names for signature");
        }
        List<String> pairs = new ArrayList<>();
        for (String name : signedFieldNames.split(",")) {
            pairs.add(name + "=" + fields.getOrDefault(name, ""));
        }
        return String.join(",", pairs);
    }

    private String mac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey());
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new PaymentProcessingException("Unable to compute Secure Acceptance signature", e);
        }
    }
}
