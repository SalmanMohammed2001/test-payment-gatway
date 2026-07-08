package com.exotic.payment.service;

import com.exotic.payment.dto.ProviderFieldError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a CyberSource REST error body into its {@code reason}, {@code message}
 * and field-level {@code details[]} entries.
 *
 * <p>Example body for reason code 101:
 * <pre>
 * {
 *   "status": "INVALID_REQUEST",
 *   "reason": "MISSING_FIELD",
 *   "message": "Declined - The request is missing one or more fields",
 *   "details": [ { "field": "orderInformation.billTo.country", "reason": "MISSING_FIELD" } ]
 * }
 * </pre>
 */
@Component
public class CyberSourceErrorParser {

    private static final Logger log = LoggerFactory.getLogger(CyberSourceErrorParser.class);

    private final ObjectMapper objectMapper;

    public CyberSourceErrorParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ParsedError(String reason, String message, List<ProviderFieldError> fieldErrors) {
        public static ParsedError empty() {
            return new ParsedError(null, null, List.of());
        }
    }

    public ParsedError parse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return ParsedError.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String reason = text(root, "reason");
            String message = text(root, "message");
            List<ProviderFieldError> fieldErrors = extractDetails(root.get("details"));
            return new ParsedError(reason, message, fieldErrors);
        } catch (Exception e) {
            log.debug("Unable to parse CyberSource error body: {}", e.getMessage());
            return ParsedError.empty();
        }
    }

    private List<ProviderFieldError> extractDetails(JsonNode details) {
        List<ProviderFieldError> result = new ArrayList<>();
        if (details != null && details.isArray()) {
            for (JsonNode detail : details) {
                result.add(new ProviderFieldError(text(detail, "field"), text(detail, "reason")));
            }
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }
}
