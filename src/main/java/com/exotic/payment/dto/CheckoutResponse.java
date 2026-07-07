package com.exotic.payment.dto;

import java.util.Map;

/**
 * Signed form data the frontend POSTs to CyberSource to launch the hosted
 * checkout.
 *
 * @param action the CyberSource endpoint the form must POST to
 * @param fields every hidden form field, including {@code signature}
 */
public record CheckoutResponse(
        String action,
        Map<String, String> fields
) {
}
