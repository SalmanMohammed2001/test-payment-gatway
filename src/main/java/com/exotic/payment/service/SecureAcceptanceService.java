package com.exotic.payment.service;

import com.exotic.payment.config.SecureAcceptanceProperties;
import com.exotic.payment.domain.PaymentStatus;
import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.domain.TransactionType;
import com.exotic.payment.dto.CheckoutRequest;
import com.exotic.payment.dto.CheckoutResponse;
import com.exotic.payment.exception.ResourceNotFoundException;
import com.exotic.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.exotic.payment.dto.CheckoutBillingDto;
import com.exotic.payment.support.CurrencySupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the CyberSource Secure Acceptance hosted-checkout flow: builds
 * the signed request the frontend submits, and processes the signed response
 * CyberSource posts back once the customer has paid.
 */
@Service
public class SecureAcceptanceService {

    private static final Logger log = LoggerFactory.getLogger(SecureAcceptanceService.class);

    private static final DateTimeFormatter SIGNED_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final SecureAcceptanceProperties properties;
    private final SecureAcceptanceSignatureService signatureService;
    private final PaymentTransactionRepository repository;

    public SecureAcceptanceService(SecureAcceptanceProperties properties,
                                   SecureAcceptanceSignatureService signatureService,
                                   PaymentTransactionRepository repository) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.repository = repository;
    }

    /**
     * Builds the signed field map for a hosted-checkout sale and records a
     * PENDING transaction keyed by the generated {@code transaction_uuid}.
     */
    @Transactional
    public CheckoutResponse createCheckout(CheckoutRequest request) {
        String currency = CurrencySupport.resolve(request.currency());
        BigDecimal amount = request.amount().setScale(2, java.math.RoundingMode.HALF_UP);
        String transactionUuid = UUID.randomUUID().toString();

        // Insertion order defines the signing order. "signed_field_names" is a
        // placeholder here and filled in once every signed field is present.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("access_key", properties.accessKey());
        fields.put("profile_id", properties.profileId());
        fields.put("transaction_uuid", transactionUuid);
        fields.put("signed_field_names", "");
        fields.put("unsigned_field_names", "");
        fields.put("signed_date_time", SIGNED_DATE_TIME.format(Instant.now()));
        fields.put("locale", properties.locale());
        fields.put("transaction_type", "sale");
        fields.put("reference_number", request.referenceCode());
        fields.put("amount", amount.toPlainString());
        fields.put("currency", currency);
        addBillingFields(fields, resolveBilling(request.billTo()));

        // Every field we send must be listed in signed_field_names (or the request
        // is rejected). Build it from the keys added so far, then sign.
        fields.put("signed_field_names", String.join(",", fields.keySet()));
        fields.put("signature", signatureService.sign(fields));

        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceCode(request.referenceCode());
        tx.setCybersourceId(transactionUuid);
        tx.setTransactionType(TransactionType.SECURE_ACCEPTANCE);
        tx.setStatus(PaymentStatus.PENDING);
        tx.setAmount(amount);
        tx.setCurrency(currency);
        tx.setProviderStatus("PENDING");
        repository.save(tx);

        log.info("Secure Acceptance checkout created ref={} uuid={} amount={} {}",
                request.referenceCode(), transactionUuid, amount, currency);
        return new CheckoutResponse(properties.payUrl(), fields);
    }

    private CheckoutBillingDto resolveBilling(CheckoutBillingDto billTo) {
        return billTo != null ? billTo : properties.resolvedDefaultBilling();
    }

    /**
     * Verifies and records a CyberSource response POST. Returns the updated
     * transaction on success.
     *
     * @throws com.exotic.payment.exception.PaymentProcessingException if the signature is invalid
     */
    @Transactional
    public PaymentTransaction handleResponse(Map<String, String> params) {
        if (!signatureService.verify(params)) {
            log.warn("Rejected Secure Acceptance response with invalid signature: ref={} uuid={}",
                    params.get("req_reference_number"), params.get("req_transaction_uuid"));
            throw new com.exotic.payment.exception.PaymentProcessingException(
                    "Invalid Secure Acceptance response signature");
        }

        String transactionUuid = params.get("req_transaction_uuid");
        String referenceNumber = params.get("req_reference_number");
        String decision = params.get("decision");
        String reasonCode = params.get("reason_code");

        // Reason code 101 = one or more required fields are missing/invalid.
        // CyberSource lists them as missingField_0..N / invalidField_0..N.
        List<String> missingFields = collectIndexedFields(params, "missingField_");
        List<String> invalidFields = collectIndexedFields(params, "invalidField_");
        if (!missingFields.isEmpty() || !invalidFields.isEmpty()) {
            log.warn("Secure Acceptance field errors ref={} reasonCode={} missing={} invalid={}",
                    referenceNumber, reasonCode, missingFields, invalidFields);
        }

        PaymentTransaction tx = repository.findFirstByCybersourceId(transactionUuid)
                .orElseGet(() -> {
                    // No matching pending row (e.g. replayed/out-of-band); record a new one.
                    PaymentTransaction created = new PaymentTransaction();
                    created.setReferenceCode(referenceNumber);
                    created.setTransactionType(TransactionType.SECURE_ACCEPTANCE);
                    return created;
                });

        tx.setStatus(mapDecision(decision));
        tx.setProviderStatus(decision);
        tx.setProviderReason(truncate(
                buildProviderReason(reasonCode, params.get("message"), missingFields, invalidFields),
                255));
        // Replace our correlation uuid with the real CyberSource transaction id.
        if (params.get("transaction_id") != null) {
            tx.setCybersourceId(params.get("transaction_id"));
        }
        if (tx.getAmount() == null && params.get("req_amount") != null) {
            tx.setAmount(new BigDecimal(params.get("req_amount")));
        }
        if (tx.getCurrency() == null) {
            tx.setCurrency(params.get("req_currency"));
        }
        tx.setRawResponse(toRawString(params));
        repository.save(tx);

        log.info("Secure Acceptance response processed ref={} decision={} reason={} status={}",
                referenceNumber, decision, params.get("reason_code"), tx.getStatus());
        return tx;
    }

    /** Returns the most recent Secure Acceptance transaction for a reference code. */
    @Transactional(readOnly = true)
    public PaymentTransaction findLatestByReference(String referenceCode) {
        return repository.findByReferenceCodeOrderByCreatedAtDesc(referenceCode).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No transaction found for referenceCode " + referenceCode));
    }

    private PaymentStatus mapDecision(String decision) {
        if (decision == null) {
            return PaymentStatus.UNKNOWN;
        }
        return switch (decision.toUpperCase()) {
            case "ACCEPT" -> PaymentStatus.CAPTURED;
            case "DECLINE" -> PaymentStatus.DECLINED;
            case "REVIEW" -> PaymentStatus.PENDING;
            case "ERROR", "CANCEL" -> PaymentStatus.FAILED;
            default -> PaymentStatus.UNKNOWN;
        };
    }

    /**
     * Serialises the response params to a compact string for the audit column,
     * omitting the signature and card fields.
     */
    private String toRawString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder("{");
        params.forEach((k, v) -> {
            if (k.equals("signature") || k.startsWith("req_card") || k.contains("account_number")) {
                return;
            }
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(k).append('=').append(v);
        });
        return sb.append('}').toString();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Adds {@code bill_to_*} fields for any billing values that are present.
     * Fields are only added (and therefore signed) when non-blank so we never
     * submit empty values that could themselves trigger reason code 101.
     */
    private void addBillingFields(Map<String, String> fields, CheckoutBillingDto billTo) {
        if (billTo == null) {
            return;
        }
        putIfPresent(fields, "bill_to_forename", billTo.firstName());
        putIfPresent(fields, "bill_to_surname", billTo.lastName());
        putIfPresent(fields, "bill_to_email", billTo.email());
        putIfPresent(fields, "bill_to_address_line1", billTo.address1());
        putIfPresent(fields, "bill_to_address_line2", billTo.address2());
        putIfPresent(fields, "bill_to_address_city", billTo.city());
        putIfPresent(fields, "bill_to_address_state", billTo.state());
        putIfPresent(fields, "bill_to_address_postal_code", billTo.postalCode());
        putIfPresent(fields, "bill_to_address_country", billTo.country());
        putIfPresent(fields, "bill_to_phone", billTo.phone());
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }

    /**
     * Collects consecutive indexed response fields such as
     * {@code missingField_0, missingField_1, ...} into an ordered list.
     */
    private static List<String> collectIndexedFields(Map<String, String> params, String prefix) {
        List<String> result = new ArrayList<>();
        for (int i = 0; params.containsKey(prefix + i); i++) {
            String value = params.get(prefix + i);
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String buildProviderReason(String reasonCode, String message,
                                              List<String> missingFields, List<String> invalidFields) {
        StringBuilder sb = new StringBuilder();
        if (reasonCode != null && !reasonCode.isBlank()) {
            sb.append("reasonCode=").append(reasonCode);
        }
        if (message != null && !message.isBlank()) {
            appendSeparator(sb).append(message);
        }
        if (!missingFields.isEmpty()) {
            appendSeparator(sb).append("missing: ").append(String.join(", ", missingFields));
        }
        if (!invalidFields.isEmpty()) {
            appendSeparator(sb).append("invalid: ").append(String.join(", ", invalidFields));
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static StringBuilder appendSeparator(StringBuilder sb) {
        if (!sb.isEmpty()) {
            sb.append(" | ");
        }
        return sb;
    }
}
