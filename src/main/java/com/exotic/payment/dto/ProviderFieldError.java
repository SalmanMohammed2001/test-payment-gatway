package com.exotic.payment.dto;

/**
 * A single problematic field reported by CyberSource, e.g. reason code 101
 * returns entries such as {@code {"field":"orderInformation.billTo.country","reason":"MISSING_FIELD"}}.
 */
public record ProviderFieldError(String field, String reason) {
}
