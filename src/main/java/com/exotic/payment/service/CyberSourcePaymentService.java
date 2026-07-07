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
import com.exotic.payment.dto.PaymentRequest;
import com.exotic.payment.dto.RefundRequest;
import com.exotic.payment.exception.PaymentProcessingException;
import com.exotic.payment.exception.ResourceNotFoundException;
import com.exotic.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class CyberSourcePaymentService {

    private static final Logger log = LoggerFactory.getLogger(CyberSourcePaymentService.class);

    private final CyberSourceClientFactory clientFactory;
    private final PaymentTransactionRepository repository;
    private final CyberSourceProperties properties;

    public CyberSourcePaymentService(CyberSourceClientFactory clientFactory,
                                     PaymentTransactionRepository repository,
                                     CyberSourceProperties properties) {
        this.clientFactory = clientFactory;
        this.repository = repository;
        this.properties = properties;
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

        ApiClient apiClient = clientFactory.newApiClient();
        PaymentsApi paymentsApi = new PaymentsApi(apiClient);

        PtsV2PaymentsPost201Response response;
        try {
            response = paymentsApi.createPayment(sdkRequest);
        } catch (ApiException e) {
            throw providerError("createPayment", e);
        } catch (Exception e) {
            throw genericError("createPayment", e);
        }

        String providerStatus = response.getStatus();
        String reason = response.getErrorInformation() != null
                ? response.getErrorInformation().getReason()
                : null;

        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceCode(request.referenceCode());
        tx.setCybersourceId(response.getId());
        tx.setTransactionType(capture ? TransactionType.SALE : TransactionType.AUTHORIZATION);
        tx.setStatus(mapAuthStatus(providerStatus));
        tx.setAmount(request.amount());
        tx.setCurrency(currency);
        tx.setProviderStatus(providerStatus);
        tx.setProviderReason(reason);
        tx.setRawResponse(safeToString(response));

        log.info("CyberSource charge ref={} id={} status={}", request.referenceCode(),
                response.getId(), providerStatus);
        return repository.save(tx);
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
        return new Ptsv2paymentsOrderInformationBillTo()
                .firstName(request.billTo().firstName())
                .lastName(request.billTo().lastName())
                .address1(request.billTo().address1())
                .locality(request.billTo().locality())
                .administrativeArea(request.billTo().administrativeArea())
                .postalCode(request.billTo().postalCode())
                .country(request.billTo().country())
                .email(request.billTo().email())
                .phoneNumber(request.billTo().phoneNumber());
    }

    private String resolveCurrency(String currency) {
        return (currency == null || currency.isBlank())
                ? properties.defaultCurrency()
                : currency.toUpperCase();
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

    private PaymentProcessingException providerError(String operation, ApiException e) {
        log.warn("CyberSource {} failed: code={} body={}", operation, e.getCode(),
                e.getResponseBody());
        return new PaymentProcessingException(
                "CyberSource " + operation + " failed", e.getCode(), e.getResponseBody(), e);
    }

    private PaymentProcessingException genericError(String operation, Exception e) {
        log.error("CyberSource {} error", operation, e);
        return new PaymentProcessingException(
                "CyberSource " + operation + " error: " + e.getMessage(), 0, null, e);
    }
}
