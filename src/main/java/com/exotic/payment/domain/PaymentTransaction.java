package com.exotic.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "payment_transaction",
        indexes = {
                @Index(name = "idx_reference_code", columnList = "reference_code"),
                @Index(name = "idx_cybersource_id", columnList = "cybersource_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Merchant-side idempotent reference for this order/transaction. */
    @Column(name = "reference_code", nullable = false, length = 100)
    private String referenceCode;

    /** Identifier returned by CyberSource for the processed transaction. */
    @Column(name = "cybersource_id", length = 100)
    private String cybersourceId;

    /** Set for capture/refund operations that follow an authorization. */
    @Column(name = "parent_cybersource_id", length = 100)
    private String parentCybersourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    /** Raw CyberSource status string (e.g. AUTHORIZED, DECLINED). */
    @Column(name = "provider_status", length = 50)
    private String providerStatus;

    @Column(name = "provider_reason", length = 255)
    private String providerReason;

    @Lob
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
