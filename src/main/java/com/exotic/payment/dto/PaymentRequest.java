package com.exotic.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank String referenceCode,

        @NotNull @DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount,

        String currency,

        /** When true, funds are authorized and captured in one step (sale). */
        boolean capture,

        @NotNull @Valid CardDto card,

        @NotNull @Valid BillingDto billTo
) {
}
