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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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

    /** Fields signed on the outbound checkout request, in signing order. */
    private static final String SIGNED_FIELD_NAMES = String.join(",",
            "access_key", "profile_id", "transaction_uuid", "signed_field_names",
            "unsigned_field_names", "signed_date_time", "locale", "transaction_type",
            "reference_number", "amount", "currency");

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
        String currency = (request.currency() == null || request.currency().isBlank())
                ? properties.defaultCurrency()
                : request.currency();
        BigDecimal amount = request.amount().setScale(2, java.math.RoundingMode.HALF_UP);
        String transactionUuid = UUID.randomUUID().toString();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("access_key", properties.accessKey());
        fields.put("profile_id", properties.profileId());
        fields.put("transaction_uuid", transactionUuid);
        fields.put("signed_field_names", SIGNED_FIELD_NAMES);
        fields.put("unsigned_field_names", "");
        fields.put("signed_date_time", SIGNED_DATE_TIME.format(Instant.now()));
        fields.put("locale", properties.locale());
        fields.put("transaction_type", "sale");
        fields.put("reference_number", request.referenceCode());
        fields.put("amount", amount.toPlainString());
        fields.put("currency", currency);
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
        tx.setProviderReason(truncate(params.get("message"), 255));
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
}
