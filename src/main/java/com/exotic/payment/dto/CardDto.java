package com.exotic.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CardDto(
        @NotBlank
        @Pattern(regexp = "\\d{12,19}", message = "card number must be 12-19 digits")
        String number,

        @NotBlank
        @Pattern(regexp = "\\d{2}", message = "expirationMonth must be MM")
        String expirationMonth,

        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "expirationYear must be YYYY")
        String expirationYear,

        @NotBlank
        @Pattern(regexp = "\\d{3,4}", message = "securityCode must be 3-4 digits")
        String securityCode
) {
}
