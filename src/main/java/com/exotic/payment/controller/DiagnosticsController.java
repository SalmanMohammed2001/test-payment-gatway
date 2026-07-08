package com.exotic.payment.controller;

import com.exotic.payment.config.CyberSourceProperties;
import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.dto.CyberSourcePaymentRequest;
import com.exotic.payment.exception.PaymentProcessingException;
import com.exotic.payment.service.CyberSourcePaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Self-test endpoint that runs a small REST authorization against CyberSource
 * and returns a plain verdict about whether the configured merchant account can
 * actually process cards. Useful for confirming account provisioning without
 * touching the hosted checkout UI.
 */
@RestController
@RequestMapping("/api/v1/diagnostics")
public class DiagnosticsController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsController.class);

    private final CyberSourcePaymentService paymentService;
    private final CyberSourceProperties properties;

    public DiagnosticsController(CyberSourcePaymentService paymentService,
                                 CyberSourceProperties properties) {
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @GetMapping("/cybersource")
    public Map<String, Object> testAuthorization() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantId", properties.merchantId());
        result.put("runEnvironment", properties.runEnvironment());

        CyberSourcePaymentRequest request = sampleAuthorization();
        try {
            PaymentTransaction tx = paymentService.charge(request);
            result.put("ok", true);
            result.put("providerStatus", tx.getProviderStatus());
            result.put("cybersourceId", tx.getCybersourceId());
            result.put("verdict", "Account CAN authorize cards. Integration is working.");
        } catch (PaymentProcessingException e) {
            result.put("ok", false);
            result.put("providerReason", e.getProviderReason());
            result.put("providerHttpStatus", e.getProviderHttpStatus());
            result.put("message", e.getMessage());
            result.put("verdict", verdictFor(e));
            log.warn("Diagnostics authorization failed: {}", e.getMessage());
        }
        return result;
    }

    private String verdictFor(PaymentProcessingException e) {
        String reason = e.getProviderReason();
        if (reason == null) {
            return "Call failed before reaching CyberSource. Check network/credentials.";
        }
        return switch (reason.toUpperCase()) {
            case "SYSTEM_ERROR", "SERVER_ERROR" ->
                    "ACCOUNT NOT PROVISIONED: CyberSource returned a system error at authorization. "
                    + "The merchant account most likely has no card processor assigned. "
                    + "Contact CyberSource support or use a freshly created sandbox account.";
            case "MISSING_FIELD", "INVALID_DATA", "INVALID_REQUEST" ->
                    "REQUEST PROBLEM: fields are missing or invalid. See message for the exact fields.";
            case "AUTHENTICATION_FAILED" ->
                    "AUTH PROBLEM: merchant credentials (key id / secret) are wrong for this environment.";
            default -> "CyberSource declined/failed with reason: " + reason;
        };
    }

    private CyberSourcePaymentRequest sampleAuthorization() {
        CyberSourcePaymentRequest.Card card = new CyberSourcePaymentRequest.Card(
                "4111111111111111", "12", "2031", "123");
        CyberSourcePaymentRequest.BillTo billTo = new CyberSourcePaymentRequest.BillTo(
                "John", "Doe", "1 Market St", "San Francisco", "CA",
                "94105", "US", "test@cybs.com", "4158880000");
        return new CyberSourcePaymentRequest(
                new CyberSourcePaymentRequest.ClientReferenceInformation("diag-" + System.currentTimeMillis()),
                new CyberSourcePaymentRequest.ProcessingInformation(false),
                new CyberSourcePaymentRequest.PaymentInformation(card),
                new CyberSourcePaymentRequest.OrderInformation(
                        new CyberSourcePaymentRequest.AmountDetails("100.00", "LKR"),
                        billTo));
    }
}
