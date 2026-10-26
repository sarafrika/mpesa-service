package com.sarafrika.apps.mpesaservice.services.daraja.products;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;

/** Reads Daraja's synchronous replies the way the B2B and B2C flows do. */
final class DarajaReplies {

    private DarajaReplies() {
    }

    /** True unless Daraja itself answered: a refusal (4xx, or a non-zero code) or nothing was sent. */
    static boolean outcomeUnknown(MpesaApiResponse<JsonNode> response) {
        if (response.success()) {
            return response.data() == null || response.data().isNull() || response.data().isMissingNode();
        }
        String code = response.error() == null ? null : response.error().errorCode();
        if (MpesaErrorResponse.NOT_SENT.equals(code) || "AUTH_FAILED".equals(code)) {
            return false;
        }
        return code == null || MpesaErrorResponse.NETWORK_ERROR.equals(code) || "INTERNAL_ERROR".equals(code)
                || response.httpStatus() >= 500 || response.httpStatus() == 408;
    }

    static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText().trim();
    }

    static String refusal(MpesaApiResponse<JsonNode> response, String descriptionField) {
        if (response.error() != null) {
            return response.error().errorMessage();
        }
        String description = text(response.data(), descriptionField);
        return description != null ? description : "Safaricom refused the request";
    }

    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
