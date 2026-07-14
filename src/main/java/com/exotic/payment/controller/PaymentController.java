package com.exotic.payment.controller;

import com.exotic.payment.dto.CaptureRequest;
import com.exotic.payment.dto.CyberSourcePaymentRequest;
import com.exotic.payment.dto.PaymentRequest;
import com.exotic.payment.dto.PaymentResponse;
import com.exotic.payment.dto.RefundRequest;
import com.exotic.payment.service.CyberSourcePaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final CyberSourcePaymentService paymentService;

    public PaymentController(CyberSourcePaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> charge(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = PaymentResponse.from(paymentService.charge(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Accepts the native CyberSource "Process a Payment" request structure
     * ({@code clientReferenceInformation} / {@code paymentInformation} /
     * {@code orderInformation}) and forwards it to CyberSource.
     */
    @PostMapping("/cybersource")
    public ResponseEntity<PaymentResponse> chargeNative(
            @Valid @RequestBody CyberSourcePaymentRequest request) {
        PaymentResponse response = PaymentResponse.from(paymentService.charge(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{cybersourceId}/captures")
    public ResponseEntity<PaymentResponse> capture(@PathVariable String cybersourceId,
                                                   @Valid @RequestBody CaptureRequest request) {
        PaymentResponse response =
                PaymentResponse.from(paymentService.capture(cybersourceId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{cybersourceId}/refundss")
    public ResponseEntity<PaymentResponse> refund(@PathVariable String cybersourceId,
                                                  @Valid @RequestBody RefundRequest request) {
        PaymentResponse response =
                PaymentResponse.from(paymentService.refund(cybersourceId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable Long id) {
        return PaymentResponse.from(paymentService.findById(id));
    }

    @GetMapping
    public List<PaymentResponse> getByReference(@org.springframework.web.bind.annotation.RequestParam String referenceCode) {
        return paymentService.findByReference(referenceCode).stream()
                .map(PaymentResponse::from)
                .toList();
    }
}
