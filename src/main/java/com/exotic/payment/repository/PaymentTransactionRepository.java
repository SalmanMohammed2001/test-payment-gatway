package com.exotic.payment.repository;

import com.exotic.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByReferenceCodeOrderByCreatedAtDesc(String referenceCode);

    Optional<PaymentTransaction> findFirstByCybersourceId(String cybersourceId);
}
