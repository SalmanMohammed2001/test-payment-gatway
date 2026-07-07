package com.exotic.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record BillingDto(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String address1,
        @NotBlank String locality,
        String administrativeArea,
        @NotBlank String postalCode,
        @NotBlank String country,
        @Email @NotBlank String email,
        String phoneNumber
) {
}
