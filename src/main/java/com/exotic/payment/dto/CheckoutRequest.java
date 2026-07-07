package com.exotic.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request to start a Secure Acceptance hosted checkout.
 *
 * @param referenceCode merchant order reference (becomes {@code reference_number})
 * @param amount        amount to charge
 * @param currency      ISO-4217 currency; falls back to the configured default when blank
 */
public record CheckoutRequest(
        @NotBlank String referenceCode,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        String currency
) {
}
