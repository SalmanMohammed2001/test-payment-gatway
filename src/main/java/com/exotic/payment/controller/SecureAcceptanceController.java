package com.exotic.payment.controller;

import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.dto.CheckoutRequest;
import com.exotic.payment.dto.CheckoutResponse;
import com.exotic.payment.dto.PaymentResponse;
import com.exotic.payment.service.SecureAcceptanceService;
import jakarta.validation.Valid;
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
import java.util.Map;

/**
 * Endpoints for the CyberSource Secure Acceptance hosted-checkout flow.
 *
 * <ul>
 *   <li>{@code POST /checkout} — returns signed fields for the frontend to POST.</li>
 *   <li>{@code POST /response} — CyberSource posts the signed result here; verified,
 *       persisted, then the browser is redirected to the frontend result page.</li>
 *   <li>{@code GET  /result}   — the frontend polls the final outcome by reference.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/secure-acceptance")
public class SecureAcceptanceController {

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
     * Receives the form-encoded, signed response from CyberSource. On success the
     * browser is 302-redirected to the frontend result page; an invalid signature
     * bubbles up as a 4xx via the global handler and is never persisted as success.
     */
    @PostMapping(path = "/response",
            consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> response(@RequestParam Map<String, String> params) {
        PaymentTransaction tx = service.handleResponse(params);
        URI redirect = UriComponentsBuilder.fromUriString(frontendResultUrl)
                .queryParam("ref", tx.getReferenceCode())
                .queryParam("status", tx.getStatus())
                .build(true)
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(redirect).build();
    }

    @GetMapping("/result")
    public PaymentResponse result(@RequestParam String referenceCode) {
        return PaymentResponse.from(service.findLatestByReference(referenceCode));
    }
}
