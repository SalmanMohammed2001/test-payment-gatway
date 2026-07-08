package com.exotic.payment.service;

import com.exotic.payment.config.CyberSourceClientFactory;
import com.exotic.payment.config.CyberSourceProperties;
import com.exotic.payment.domain.PaymentStatus;
import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.domain.TransactionType;
import com.exotic.payment.dto.BillingDto;
import com.exotic.payment.dto.CardDto;
import com.exotic.payment.dto.PaymentRequest;
import com.exotic.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CyberSourcePaymentServiceIdempotencyTest {

    @Mock
    private CyberSourceClientFactory clientFactory;
    @Mock
    private PaymentTransactionRepository repository;
    @Mock
    private CyberSourceProperties properties;
    @Mock
    private CyberSourceErrorParser errorParser;

    @InjectMocks
    private CyberSourcePaymentService service;

    private static PaymentRequest sampleRequest() {
        return new PaymentRequest(
                "ORDER-123",
                new BigDecimal("10.00"),
                "LKR",
                true,
                new CardDto("4111111111111111", "12", "2031", "123"),
                new BillingDto("Jane", "Doe", "1 Main St", "Colombo",
                        null, "00100", "LK", "jane@example.com", null));
    }

    @Test
    void charge_returnsExistingTransaction_withoutCallingCyberSource() {
        PaymentTransaction existing = new PaymentTransaction();
        existing.setReferenceCode("ORDER-123");
        existing.setCybersourceId("CYBS-999");
        existing.setTransactionType(TransactionType.SALE);
        existing.setStatus(PaymentStatus.AUTHORIZED);

        when(repository.findFirstByReferenceCodeAndTransactionTypeInAndStatusNotInOrderByCreatedAtDesc(
                any(String.class), any(Collection.class), any(Collection.class)))
                .thenReturn(Optional.of(existing));

        PaymentTransaction result = service.charge(sampleRequest());

        assertThat(result).isSameAs(existing);
        // The idempotent path must neither open an SDK client nor persist a new row.
        verifyNoInteractions(clientFactory);
        verify(repository, never()).save(any(PaymentTransaction.class));
    }
}
