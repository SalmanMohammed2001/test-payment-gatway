package com.exotic.payment.repository;

import com.exotic.payment.domain.PaymentStatus;
import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByReferenceCodeOrderByCreatedAtDesc(String referenceCode);

    Optional<PaymentTransaction> findFirstByCybersourceId(String cybersourceId);

    /**
     * Finds the most recent non-terminal-failure charge for a reference code.
     * Used to make {@code charge} idempotent: a client that retries the same
     * reference after a successful (or still-pending) charge gets the existing
     * transaction back instead of being charged twice.
     */
    Optional<PaymentTransaction> findFirstByReferenceCodeAndTransactionTypeInAndStatusNotInOrderByCreatedAtDesc(
            String referenceCode,
            Collection<TransactionType> transactionTypes,
            Collection<PaymentStatus> excludedStatuses);
}
