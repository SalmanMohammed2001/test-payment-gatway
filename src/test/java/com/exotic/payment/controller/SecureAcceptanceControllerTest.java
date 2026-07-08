package com.exotic.payment.controller;

import com.exotic.payment.config.SecureAcceptanceProperties;
import com.exotic.payment.domain.PaymentStatus;
import com.exotic.payment.domain.PaymentTransaction;
import com.exotic.payment.domain.TransactionType;
import com.exotic.payment.dto.CheckoutResponse;
import com.exotic.payment.service.SecureAcceptanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecureAcceptanceController.class)
class SecureAcceptanceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SecureAcceptanceService service;

    @TestConfiguration
    static class Config {
        @Bean
        SecureAcceptanceService service() {
            return mock(SecureAcceptanceService.class);
        }

        @Bean
        SecureAcceptanceProperties properties() {
            return new SecureAcceptanceProperties("profile", "access", "secret",
                    "https://testsecureacceptance.cybersource.com/pay",
                    "http://localhost:4200/checkout/result", "en", "LKR", null);
        }
    }

    @Test
    void checkoutReturnsSignedFields() throws Exception {
        when(service.createCheckout(any())).thenReturn(new CheckoutResponse(
                "https://testsecureacceptance.cybersource.com/pay",
                Map.of("profile_id", "profile", "signature", "sig123")));

        mockMvc.perform(post("/api/v1/secure-acceptance/checkout")
                        .contentType("application/json")
                        .content("""
                                {"referenceCode":"order-1001","amount":10000.00,"currency":"LKR",
                                "billTo":{"firstName":"John","lastName":"Doe","email":"test@cybs.com",
                                "address1":"1 Market St","address2":"Suite 100","city":"SF","state":"CA",
                                "postalCode":"94105","country":"US","phone":"4158880000"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("https://testsecureacceptance.cybersource.com/pay"))
                .andExpect(jsonPath("$.fields.signature").value("sig123"));
    }

    @Test
    void checkoutRejectsInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/secure-acceptance/checkout")
                        .contentType("application/json")
                        .content("{\"amount\":102.21}")) // missing referenceCode
                .andExpect(status().isBadRequest());
    }

    @Test
    void responseRedirectsToFrontend() throws Exception {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceCode("order-1001");
        tx.setStatus(PaymentStatus.CAPTURED);
        when(service.handleResponse(any())).thenReturn(tx);

        mockMvc.perform(post("/api/v1/secure-acceptance/response")
                        .contentType("application/x-www-form-urlencoded")
                        .content("decision=ACCEPT&req_reference_number=order-1001&signature=x"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:4200/checkout/result?ref=order-1001&status=CAPTURED"));
    }

    @Test
    void resultReturnsTransaction() throws Exception {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceCode("order-1001");
        tx.setTransactionType(TransactionType.SECURE_ACCEPTANCE);
        tx.setStatus(PaymentStatus.CAPTURED);
        tx.setAmount(new BigDecimal("102.21"));
        tx.setCurrency("LKR");
        when(service.findLatestByReference(eq("order-1001"))).thenReturn(tx);

        mockMvc.perform(get("/api/v1/secure-acceptance/result").param("referenceCode", "order-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceCode").value("order-1001"))
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }
}
