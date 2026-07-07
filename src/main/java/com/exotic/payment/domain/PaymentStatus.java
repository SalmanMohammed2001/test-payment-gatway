package com.exotic.payment.domain;

public enum PaymentStatus {
    AUTHORIZED,
    PENDING,
    PARTIAL_AUTHORIZED,
    CAPTURED,
    REFUNDED,
    DECLINED,
    FAILED,
    UNKNOWN
}
