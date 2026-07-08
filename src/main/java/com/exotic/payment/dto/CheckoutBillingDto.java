package com.exotic.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Billing details for Secure Acceptance checkout. Maps to CyberSource
 * {@code bill_to_*} fields matching the Business Center Billing Information form:
 * First Name, Last Name, Street Address 1/2, City, Zip/Postal Code, Country,
 * Phone Number, Email (and optional State for US/Canada).
 */
public record CheckoutBillingDto(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email @NotBlank String email,
        @NotBlank String address1,
        String address2,
        @NotBlank String city,
        String state,
        @NotBlank String postalCode,
        @NotBlank String country,
        @NotBlank String phone
) {
}
