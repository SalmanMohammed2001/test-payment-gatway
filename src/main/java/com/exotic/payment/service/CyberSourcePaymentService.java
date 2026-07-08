package com.exotic.payment.service;

import Api.CaptureApi;
import Api.PaymentsApi;
import Api.RefundApi;
import Invokers.ApiClient;
import Invokers.ApiException;
import Model.CapturePaymentRequest;
import Model.CreatePaymentRequest;
import Model.PtsV2PaymentsCapturesPost201Response;
import Model.PtsV2PaymentsPost201Response;
import Model.PtsV2PaymentsRefundPost201Response;
import Model.Ptsv2paymentsClientReferenceInformation;
import Model.Ptsv2paymentsOrderInformation;
import Model.Ptsv2paymentsOrderInformationAmountDetails;
import Model.Ptsv2paymentsOrderInformationBillTo;
import Model.Ptsv2paymentsPaymentInformation;
import Model.Ptsv2paymentsPaymentInformationCard;
import Model.Ptsv2paymentsProcessingInformation;
import Model.Ptsv2paymentsidcapturesOrderInformation;
import Model.Ptsv2paymentsidcapturesOrderInformationAmountDetails;
import Model.Ptsv2paymentsidrefundsClientReferenceInformation;
import Model.Ptsv2paymentsidrefundsOrderInformation;
import Model.RefundPaymentRequest;
import com.exotic.payment.config.CyberSourceClientFactory;
import com.exotic.payment.config.CyberSourceProperties;
import com.exotic.payment.domain.PaymentStatus;
import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.domain.TransactionType;
import com.exotic.payment.dto.CaptureRequest;
import com.exotic.payment.dto.CyberSourcePaymentRequest;
import com.exotic.payment.dto.PaymentRequest;
import com.exotic.payment.dto.ProviderFieldError;
import com.exotic.payment.dto.RefundRequest;
import com.exotic.payment.exception.PaymentProcessingException;
import com.exotic.payment.exception.ResourceNotFoundException;
import com.exotic.payment.repository.PaymentTransactionRepository;
import com.exotic.payment.support.CurrencySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class CyberSourcePaymentService {

    private static final Logger log = LoggerFactory.getLogger(CyberSourcePaymentService.class);

    private final CyberSourceClientFactory clientFactory;
    private final PaymentTransactionRepository repository;
    private final CyberSourceProperties properties;
    private final CyberSourceErrorParser errorParser;

    public CyberSourcePaymentService(CyberSourceClientFactory clientFactory,
                                     PaymentTransactionRepository repository,
                                     CyberSourceProperties properties,
                                     CyberSourceErrorParser errorParser) {
        this.clientFactory = clientFactory;
        this.repository = repository;
        this.properties = properties;
        this.errorParser = errorParser;
    }

    /**
     * Authorizes (and optionally captures) a card payment.
     */
    public PaymentTransaction charge(PaymentRequest request) {
        String currency = resolveCurrency(request.currency());
        boolean capture = request.capture();

        CreatePaymentRequest sdkRequest = new CreatePaymentRequest()
                .clientReferenceInformation(new Ptsv2paymentsClientReferenceInformation()
                        .code(request.referenceCode()))
                .processingInformation(new Ptsv2paymentsProcessingInformation()
                        .capture(capture))
                .paymentInformation(new Ptsv2paymentsPaymentInformation()
                        .card(new Ptsv2paymentsPaymentInformationCard()
                                .number(request.card().number())
                                .expirationMonth(request.card().expirationMonth())
                                .expirationYear(request.card().expirationYear())
                                .securityCode(request.card().securityCode())))
                .orderInformation(new Ptsv2paymentsOrderInformation()
                        .amountDetails(new Ptsv2paymentsOrderInformationAmountDetails()
                                .totalAmount(formatAmount(request.amount()))
                                .currency(currency))
                        .billTo(buildBillTo(request)));

        return executeCharge(sdkRequest, request.referenceCode(), request.amount(), currency, capture);
    }

    /**
     * Authorizes (and optionally captures) a card payment using the native
     * CyberSource REST request structure.
     */
    public PaymentTransaction charge(CyberSourcePaymentRequest request) {
        CyberSourcePaymentRequest.AmountDetails amountDetails =
                request.orderInformation().amountDetails();
        String currency = resolveCurrency(amountDetails.currency());
        boolean capture = request.processingInformation() != null
                && Boolean.TRUE.equals(request.processingInformation().capture());
        BigDecimal amount = parseAmount(amountDetails.totalAmount());
        String referenceCode = request.clientReferenceInformation().code();

        CreatePaymentRequest sdkRequest = new CreatePaymentRequest()
                .clientReferenceInformation(new Ptsv2paymentsClientReferenceInformation()
                        .code(referenceCode))
                .processingInformation(new Ptsv2paymentsProcessingInformation()
                        .capture(capture))
                .paymentInformation(buildPaymentInformation(request.paymentInformation()))
                .orderInformation(new Ptsv2paymentsOrderInformation()
                        .amountDetails(new Ptsv2paymentsOrderInformationAmountDetails()
                                .totalAmount(formatAmount(amount))
                                .currency(currency))
                        .billTo(buildBillTo(request.orderInformation().billTo())));

        return executeCharge(sdkRequest, referenceCode, amount, currency, capture);
    }

    private PaymentTransaction executeCharge(CreatePaymentRequest sdkRequest,
                                             String referenceCode,
                                             BigDecimal amount,
                                             String currency,
                                             boolean capture) {
        PtsV2PaymentsPost201Response response;
        try {
            response = createPaymentWithRetry(sdkRequest, referenceCode);
        } catch (PaymentProcessingException e) {
            // Record the failed attempt so the transaction is never silently lost,
            // then let the caller/handler surface the error.
            recordFailedAttempt(referenceCode, amount, currency, capture, e);
            throw e;
        }

        String providerStatus = response.getStatus();
        String reason = response.getErrorInformation() != null
                ? response.getErrorInformation().getReason()
                : null;

        if (isInvalidRequest(providerStatus) && response.getErrorInformation() != null) {
            throw fieldError("createPayment", reason,
                    response.getErrorInformation().getMessage(),
                    mapDetails(response.getErrorInformation().getDetails()));
        }

        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceCode(referenceCode);
        tx.setCybersourceId(response.getId());
        tx.setTransactionType(capture ? TransactionType.SALE : TransactionType.AUTHORIZATION);
        tx.setStatus(mapAuthStatus(providerStatus));
        tx.setAmount(amount);
        tx.setCurrency(currency);
        tx.setProviderStatus(providerStatus);
        tx.setProviderReason(reason);
        tx.setRawResponse(safeToString(response));

        log.info("CyberSource charge ref={} id={} status={}", referenceCode,
                response.getId(), providerStatus);
        return repository.save(tx);
    }

    /**
     * Calls {@code createPayment} with a <strong>bounded</strong> retry policy.
     *
     * <p>Per CyberSource guidance, a SYSTEM_ERROR (HTTP 5xx) may be a genuine
     * system outage or a processor rejection of invalid data. We therefore retry
     * <em>only</em> server-side/system errors, a fixed number of times with
     * exponential backoff, and never endlessly. Client errors (4xx: declines,
     * invalid data) are permanent and are surfaced immediately without retry.
     */
    private PtsV2PaymentsPost201Response createPaymentWithRetry(CreatePaymentRequest sdkRequest,
                                                               String referenceCode) {
        int maxAttempts = properties.retry().maxAttempts();
        long backoff = properties.retry().initialBackoff();
        long maxBackoff = properties.retry().maxBackoff();
        PaymentProcessingException lastError;

        for (int attempt = 1; ; attempt++) {
            // A fresh client per attempt; the SDK client is stateful.
            PaymentsApi paymentsApi = new PaymentsApi(clientFactory.newApiClient());
            try {
                return paymentsApi.createPayment(sdkRequest);
            } catch (ApiException e) {
                lastError = providerError("createPayment", e);
                // Permanent (4xx) errors: never retry.
                if (!isRetryableHttpStatus(e.getCode())) {
                    throw lastError;
                }
            } catch (Exception e) {
                // Network/timeout style failure with no HTTP status: retry-eligible.
                lastError = genericError("createPayment", e);
            }

            // Bounded: stop once attempts are exhausted (never endless).
            if (attempt >= maxAttempts) {
                log.error("CyberSource createPayment giving up after {} attempt(s) ref={}",
                        maxAttempts, referenceCode);
                throw lastError;
            }

            log.warn("CyberSource createPayment system error ref={} attempt={}/{}; retrying in {}ms",
                    referenceCode, attempt, maxAttempts, backoff);
            sleepQuietly(backoff);
            backoff = Math.min(backoff * 2, maxBackoff);
        }
    }

    /**
     * Persists a FAILED record for a charge that could not be completed, so the
     * attempt is auditable and no idempotent reference silently disappears.
     */
    private void recordFailedAttempt(String referenceCode, BigDecimal amount, String currency,
                                     boolean capture, PaymentProcessingException e) {
        try {
            PaymentTransaction tx = new PaymentTransaction();
            tx.setReferenceCode(referenceCode);
            tx.setTransactionType(capture ? TransactionType.SALE : TransactionType.AUTHORIZATION);
            tx.setStatus(PaymentStatus.FAILED);
            tx.setAmount(amount);
            tx.setCurrency(currency);
            tx.setProviderStatus(truncate(
                    e.getProviderReason() != null ? e.getProviderReason() : "SYSTEM_ERROR", 50));
            tx.setProviderReason(truncate(e.getMessage(), 255));
            tx.setRawResponse(e.getProviderBody());
            repository.save(tx);
        } catch (Exception persistError) {
            log.error("Failed to persist failed payment attempt ref={}", referenceCode, persistError);
        }
    }

    /**
     * Whether an HTTP status from CyberSource is a server-side/system error that
     * is eligible for a bounded retry. 4xx (declines, invalid data) are not.
     */
    private static boolean isRetryableHttpStatus(int code) {
        return code == 0 || code >= 500;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new PaymentProcessingException("Payment retry interrupted");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Captures funds for a previously authorized payment.
     */
    public PaymentTransaction capture(String cybersourcePaymentId, CaptureRequest request) {
        String currency = resolveCurrency(request.currency());

        CapturePaymentRequest sdkRequest = new CapturePaymentRequest()
                .clientReferenceInformation(new Ptsv2paymentsClientReferenceInformation()
                        .code(request.referenceCode()))
                .orderInformation(new Ptsv2paymentsidcapturesOrderInformation()
                        .amountDetails(new Ptsv2paymentsidcapturesOrderInformationAmountDetails()
                                .totalAmount(formatAmount(request.amount()))
                                .currency(currency)));

        ApiClient apiClient = clientFactory.newApiClient();
        CaptureApi captureApi = new CaptureApi(apiClient);

        PtsV2PaymentsCapturesPost201Response response;
        try {
            response = captureApi.capturePayment(sdkRequest, cybersourcePaymentId);
        } catch (ApiException e) {
            throw providerError("capturePayment", e);
        } catch (Exception e) {
            throw genericError("capturePayment", e);
        }

        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceCode(request.referenceCode());
        tx.setCybersourceId(response.getId());
        tx.setParentCybersourceId(cybersourcePaymentId);
        tx.setTransactionType(TransactionType.CAPTURE);
        tx.setStatus(isAccepted(response.getStatus()) ? PaymentStatus.CAPTURED
                : mapAuthStatus(response.getStatus()));
        tx.setAmount(request.amount());
        tx.setCurrency(currency);
        tx.setProviderStatus(response.getStatus());
        tx.setRawResponse(safeToString(response));

        log.info("CyberSource capture parent={} id={} status={}", cybersourcePaymentId,
                response.getId(), response.getStatus());
        return repository.save(tx);
    }

    /**
     * Refunds a previously captured payment (full or partial).
     */
    public PaymentTransaction refund(String cybersourcePaymentId, RefundRequest request) {
        String currency = resolveCurrency(request.currency());

        RefundPaymentRequest sdkRequest = new RefundPaymentRequest()
                .clientReferenceInformation(new Ptsv2paymentsidrefundsClientReferenceInformation()
                        .code(request.referenceCode()))
                .orderInformation(new Ptsv2paymentsidrefundsOrderInformation()
                        .amountDetails(new Ptsv2paymentsidcapturesOrderInformationAmountDetails()
                                .totalAmount(formatAmount(request.amount()))
                                .currency(currency)));

        ApiClient apiClient = clientFactory.newApiClient();
        RefundApi refundApi = new RefundApi(apiClient);

        PtsV2PaymentsRefundPost201Response response;
        try {
            response = refundApi.refundPayment(sdkRequest, cybersourcePaymentId);
        } catch (ApiException e) {
            throw providerError("refundPayment", e);
        } catch (Exception e) {
            throw genericError("refundPayment", e);
        }

        String reason = response.getErrorInformation() != null
                ? response.getErrorInformation().getReason()
                : null;

        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceCode(request.referenceCode());
        tx.setCybersourceId(response.getId());
        tx.setParentCybersourceId(cybersourcePaymentId);
        tx.setTransactionType(TransactionType.REFUND);
        tx.setStatus(isAccepted(response.getStatus()) ? PaymentStatus.REFUNDED
                : mapAuthStatus(response.getStatus()));
        tx.setAmount(request.amount());
        tx.setCurrency(currency);
        tx.setProviderStatus(response.getStatus());
        tx.setProviderReason(reason);
        tx.setRawResponse(safeToString(response));

        log.info("CyberSource refund parent={} id={} status={}", cybersourcePaymentId,
                response.getId(), response.getStatus());
        return repository.save(tx);
    }

    public List<PaymentTransaction> findByReference(String referenceCode) {
        List<PaymentTransaction> transactions =
                repository.findByReferenceCodeOrderByCreatedAtDesc(referenceCode);
        if (transactions.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No transactions found for reference: " + referenceCode);
        }
        return transactions;
    }

    public PaymentTransaction findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found: " + id));
    }

    private Ptsv2paymentsOrderInformationBillTo buildBillTo(PaymentRequest request) {
        Ptsv2paymentsOrderInformationBillTo billTo = new Ptsv2paymentsOrderInformationBillTo()
                .firstName(request.billTo().firstName())
                .lastName(request.billTo().lastName())
                .address1(request.billTo().address1())
                .locality(request.billTo().locality())
                .postalCode(request.billTo().postalCode())
                .country(request.billTo().country())
                .email(request.billTo().email());
        // Optional fields: only send when present so we never submit empty
        // strings, which CyberSource rejects with reason code 101.
        if (hasText(request.billTo().administrativeArea())) {
            billTo.administrativeArea(request.billTo().administrativeArea());
        }
        if (hasText(request.billTo().phoneNumber())) {
            billTo.phoneNumber(request.billTo().phoneNumber());
        }
        return billTo;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Ptsv2paymentsPaymentInformation buildPaymentInformation(
            CyberSourcePaymentRequest.PaymentInformation paymentInformation) {
        CyberSourcePaymentRequest.Card card = paymentInformation.card();
        Ptsv2paymentsPaymentInformationCard sdkCard = new Ptsv2paymentsPaymentInformationCard()
                .number(card.number())
                .expirationMonth(card.expirationMonth())
                .expirationYear(card.expirationYear());
        if (hasText(card.securityCode())) {
            sdkCard.securityCode(card.securityCode());
        }
        return new Ptsv2paymentsPaymentInformation().card(sdkCard);
    }

    private Ptsv2paymentsOrderInformationBillTo buildBillTo(CyberSourcePaymentRequest.BillTo billTo) {
        Ptsv2paymentsOrderInformationBillTo sdkBillTo = new Ptsv2paymentsOrderInformationBillTo();
        if (billTo == null) {
            return sdkBillTo;
        }
        // Only send fields that are present so we never submit empty strings,
        // which CyberSource rejects with reason code 101.
        setIfPresent(billTo.firstName(), sdkBillTo::firstName);
        setIfPresent(billTo.lastName(), sdkBillTo::lastName);
        setIfPresent(billTo.address1(), sdkBillTo::address1);
        setIfPresent(billTo.locality(), sdkBillTo::locality);
        setIfPresent(billTo.administrativeArea(), sdkBillTo::administrativeArea);
        setIfPresent(billTo.postalCode(), sdkBillTo::postalCode);
        setIfPresent(billTo.country(), sdkBillTo::country);
        setIfPresent(billTo.email(), sdkBillTo::email);
        setIfPresent(billTo.phoneNumber(), sdkBillTo::phoneNumber);
        return sdkBillTo;
    }

    private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private static BigDecimal parseAmount(String totalAmount) {
        try {
            return new BigDecimal(totalAmount.trim());
        } catch (RuntimeException e) {
            throw new PaymentProcessingException(
                    "orderInformation.amountDetails.totalAmount is not a valid amount: " + totalAmount);
        }
    }

    private String resolveCurrency(String currency) {
        return CurrencySupport.resolve(currency);
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static boolean isAccepted(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase()) {
            case "PENDING", "TRANSMITTED", "ACCEPTED", "COMPLETED", "AUTHORIZED" -> true;
            default -> false;
        };
    }

    private static PaymentStatus mapAuthStatus(String status) {
        if (status == null) {
            return PaymentStatus.UNKNOWN;
        }
        return switch (status.toUpperCase()) {
            case "AUTHORIZED" -> PaymentStatus.AUTHORIZED;
            case "PARTIAL_AUTHORIZED" -> PaymentStatus.PARTIAL_AUTHORIZED;
            case "AUTHORIZED_PENDING_REVIEW", "AUTHORIZED_RISK_DECLINED", "PENDING",
                 "PENDING_AUTHENTICATION", "PENDING_REVIEW" -> PaymentStatus.PENDING;
            case "DECLINED", "AUTHENTICATION_FAILED" -> PaymentStatus.DECLINED;
            case "INVALID_REQUEST", "SERVER_ERROR" -> PaymentStatus.FAILED;
            default -> PaymentStatus.UNKNOWN;
        };
    }

    private static String safeToString(Object response) {
        try {
            return response == null ? null : response.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isInvalidRequest(String status) {
        return status != null && "INVALID_REQUEST".equalsIgnoreCase(status);
    }

    private static List<ProviderFieldError> mapDetails(
            List<Model.PtsV2PaymentsPost201ResponseErrorInformationDetails> details) {
        List<ProviderFieldError> result = new ArrayList<>();
        if (details != null) {
            for (Model.PtsV2PaymentsPost201ResponseErrorInformationDetails d : details) {
                result.add(new ProviderFieldError(d.getField(), d.getReason()));
            }
        }
        return result;
    }

    private PaymentProcessingException providerError(String operation, ApiException e) {
        String body = e.getResponseBody();
        CyberSourceErrorParser.ParsedError parsed = errorParser.parse(body);
        log.warn("CyberSource {} failed: code={} reason={} fields={} body={}", operation,
                e.getCode(), parsed.reason(), parsed.fieldErrors(), body);
        return new PaymentProcessingException(
                buildMessage(operation, parsed),
                e.getCode(), body, parsed.reason(), parsed.fieldErrors(), e);
    }

    private PaymentProcessingException fieldError(String operation,
                                                 String reason, String message,
                                                 List<ProviderFieldError> fieldErrors) {
        CyberSourceErrorParser.ParsedError parsed =
                new CyberSourceErrorParser.ParsedError(reason, message, fieldErrors);
        log.warn("CyberSource {} rejected: reason={} fields={}", operation, reason, fieldErrors);
        return new PaymentProcessingException(
                buildMessage(operation, parsed), 0, null, reason, fieldErrors, null);
    }

    private PaymentProcessingException genericError(String operation, Exception e) {
        log.error("CyberSource {} error", operation, e);
        return new PaymentProcessingException(
                "CyberSource " + operation + " error: " + e.getMessage(), 0, null, e);
    }

    private static String buildMessage(String operation, CyberSourceErrorParser.ParsedError parsed) {
        StringBuilder sb = new StringBuilder("CyberSource ").append(operation).append(" failed");
        if (parsed.reason() != null) {
            sb.append(" (").append(parsed.reason()).append(")");
        }
        if (parsed.message() != null) {
            sb.append(": ").append(parsed.message());
        }
        if (!parsed.fieldErrors().isEmpty()) {
            List<String> fields = new ArrayList<>();
            for (ProviderFieldError fe : parsed.fieldErrors()) {
                fields.add(fe.field() + " [" + fe.reason() + "]");
            }
            sb.append(" - problem fields: ").append(String.join(", ", fields));
        }
        return sb.toString();
    }
}
