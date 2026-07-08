package com.exotic.payment.controller;

import com.exotic.payment.domain.PaymentStatus;
import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.dto.CheckoutRequest;
import com.exotic.payment.dto.CheckoutResponse;
import com.exotic.payment.dto.PaymentResponse;
import com.exotic.payment.service.SecureAcceptanceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints for the CyberSource Secure Acceptance hosted-checkout flow.
 */
@RestController
@RequestMapping("/api/v1/secure-acceptance")
public class SecureAcceptanceController {

    private static final Logger log = LoggerFactory.getLogger(SecureAcceptanceController.class);

    private final SecureAcceptanceService service;
    private final String frontendResultUrl;

    public SecureAcceptanceController(SecureAcceptanceService service,
                                      com.exotic.payment.config.SecureAcceptanceProperties properties) {
        this.service = service;
        this.frontendResultUrl = properties.frontendResultUrl();
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(service.createCheckout(request));
    }

    /**
     * Receives the form-encoded, signed response from CyberSource. Always
     * redirects the customer's browser to the frontend result page — even on
     * verification failures — so they never see a raw JSON error.
     */
    @PostMapping(path = "/response", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> response(@RequestParam Map<String, String> params) {
        try {
            PaymentTransaction tx = service.handleResponse(params);
            return redirect(buildRedirectParams(tx));
        } catch (Exception e) {
            log.warn("Secure Acceptance response handling failed ref={}: {}",
                    params.get("req_reference_number"), e.getMessage());
            return redirect(buildErrorRedirectParams(params, e.getMessage()));
        }
    }

    @GetMapping("/result")
    public PaymentResponse result(@RequestParam String referenceCode) {
        return PaymentResponse.from(service.findLatestByReference(referenceCode));
    }

    /**
     * Returns the URLs to configure in the Business Center profile.
     */
    @GetMapping("/setup")
    public Map<String, String> setup(@RequestParam(required = false) String publicBaseUrl) {
        String base = (publicBaseUrl == null || publicBaseUrl.isBlank())
                ? "https://YOUR_PUBLIC_HOST"
                : publicBaseUrl.replaceAll("/$", "");
        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("transactionResponsePageUrl", base + "/api/v1/secure-acceptance/response");
        urls.put("webhookAliasUrl", base + "/api/cybersource/webhook");
        urls.put("customRedirectAfterCheckout", frontendResultUrl);
        urls.put("note", "Promote profile in Business Center after saving. Disable hosted Billing step "
                + "when bill_to_* fields are sent from this backend.");
        return urls;
    }

    private ResponseEntity<Void> redirect(Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendResultUrl);
        queryParams.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                builder.queryParam(k, v);
            }
        });
        URI location = builder.build(true).toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private static Map<String, String> buildRedirectParams(PaymentTransaction tx) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ref", tx.getReferenceCode());
        params.put("status", tx.getStatus().name());
        if (tx.getProviderStatus() != null) {
            params.put("decision", tx.getProviderStatus());
        }
        if (tx.getProviderReason() != null) {
            params.put("message", tx.getProviderReason());
        }
        return params;
    }

    private static Map<String, String> buildErrorRedirectParams(Map<String, String> cyberParams,
                                                                 String errorMessage) {
        Map<String, String> params = new LinkedHashMap<>();
        String ref = cyberParams.get("req_reference_number");
        if (ref != null) {
            params.put("ref", ref);
        }
        params.put("status", PaymentStatus.FAILED.name());
        String decision = cyberParams.get("decision");
        if (decision != null) {
            params.put("decision", decision);
        }
        String reasonCode = cyberParams.get("reason_code");
        String message = cyberParams.get("message");
        StringBuilder detail = new StringBuilder();
        if (reasonCode != null) {
            detail.append("reasonCode=").append(reasonCode);
        }
        if (message != null && !message.isBlank()) {
            if (!detail.isEmpty()) {
                detail.append(" | ");
            }
            detail.append(message);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            if (!detail.isEmpty()) {
                detail.append(" | ");
            }
            detail.append(errorMessage);
        }
        if (!detail.isEmpty()) {
            params.put("message", detail.toString());
        }
        return params;
    }
}
