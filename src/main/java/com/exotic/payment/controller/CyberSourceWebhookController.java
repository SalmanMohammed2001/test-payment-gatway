package com.exotic.payment.controller;

import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.service.SecureAcceptanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Alias endpoint for profiles that point the CyberSource Transaction Response
 * Page or Custom Redirect to {@code /api/cybersource/webhook} (as configured
 * in the Business Center). Delegates to the same handler as
 * {@code /api/v1/secure-acceptance/response}.
 */
@RestController
@RequestMapping("/api/cybersource")
public class CyberSourceWebhookController {

    private static final Logger log = LoggerFactory.getLogger(CyberSourceWebhookController.class);

    private final SecureAcceptanceService service;
    private final String frontendResultUrl;

    public CyberSourceWebhookController(SecureAcceptanceService service,
                                        com.exotic.payment.config.SecureAcceptanceProperties properties) {
        this.service = service;
        this.frontendResultUrl = properties.frontendResultUrl();
    }

    @PostMapping(path = "/webhook", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> webhook(@RequestParam Map<String, String> params) {
        log.info("CyberSource webhook received ref={} decision={}",
                params.get("req_reference_number"), params.get("decision"));
        try {
            PaymentTransaction tx = service.handleResponse(params);
            return redirect(tx.getReferenceCode(), tx.getStatus().name(),
                    tx.getProviderStatus(), tx.getProviderReason());
        } catch (Exception e) {
            log.warn("CyberSource webhook failed: {}", e.getMessage());
            return redirect(
                    params.get("req_reference_number"),
                    "FAILED",
                    params.get("decision"),
                    buildMessage(params, e.getMessage()));
        }
    }

    private ResponseEntity<Void> redirect(String ref, String status, String decision, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendResultUrl);
        if (ref != null) {
            builder.queryParam("ref", ref);
        }
        builder.queryParam("status", status);
        if (decision != null) {
            builder.queryParam("decision", decision);
        }
        if (message != null && !message.isBlank()) {
            builder.queryParam("message", message);
        }
        URI location = builder.build(true).toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private static String buildMessage(Map<String, String> params, String error) {
        StringBuilder sb = new StringBuilder();
        String reasonCode = params.get("reason_code");
        if (reasonCode != null) {
            sb.append("reasonCode=").append(reasonCode);
        }
        String msg = params.get("message");
        if (msg != null && !msg.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append(msg);
        }
        if (error != null && !error.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append(error);
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
