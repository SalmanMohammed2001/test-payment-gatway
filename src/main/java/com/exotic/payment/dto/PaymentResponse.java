package com.exotic.payment.dto;

import com.exotic.payment.domain.PaymentStatus;
import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.domain.TransactionType;

import java.math.BigDecimal;

public record PaymentResponse(
        Long id,
        String referenceCode,
        String cybersourceId,
        TransactionType transactionType,
        PaymentStatus status,
        String providerStatus,
        String providerReason,
        BigDecimal amount,
        String currency
) {
    public static PaymentResponse from(PaymentTransaction tx) {
        return new PaymentResponse(
                tx.getId(),
                tx.getReferenceCode(),
                tx.getCybersourceId(),
                tx.getTransactionType(),
                tx.getStatus(),
                tx.getProviderStatus(),
                tx.getProviderReason(),
                tx.getAmount(),
                tx.getCurrency()
        );
    }
}
