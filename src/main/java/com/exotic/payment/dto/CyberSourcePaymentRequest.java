package com.exotic.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Mirrors the CyberSource REST "Process a Payment" request body so callers can
 * post the native structure directly:
 *
 * <pre>
 * {
 *   "clientReferenceInformation": { "code": "TC50171_3" },
 *   "processingInformation":      { "capture": true },
 *   "paymentInformation":         { "card": { "number": "...", "expirationMonth": "12", "expirationYear": "2031" } },
 *   "orderInformation":           { "amountDetails": { "totalAmount": "102.21", "currency": "USD" },
 *                                   "billTo": { ... } }
 * }
 * </pre>
 */
public record CyberSourcePaymentRequest(
        @NotNull @Valid ClientReferenceInformation clientReferenceInformation,
        @Valid ProcessingInformation processingInformation,
        @NotNull @Valid PaymentInformation paymentInformation,
        @NotNull @Valid OrderInformation orderInformation
) {

    public record ClientReferenceInformation(
            @NotBlank String code
    ) {
    }

    public record ProcessingInformation(
            Boolean capture
    ) {
    }

    public record PaymentInformation(
            @NotNull @Valid Card card
    ) {
    }

    public record Card(
            @NotBlank String number,
            @NotBlank String expirationMonth,
            @NotBlank String expirationYear,
            String securityCode
    ) {
    }

    public record OrderInformation(
            @NotNull @Valid AmountDetails amountDetails,
            @Valid BillTo billTo
    ) {
    }

    public record AmountDetails(
            @NotBlank String totalAmount,
            String currency
    ) {
    }

    public record BillTo(
            String firstName,
            String lastName,
            String address1,
            String locality,
            String administrativeArea,
            String postalCode,
            String country,
            String email,
            String phoneNumber
    ) {
    }
}
