package com.sarafrika.apps.mpesaservice.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;

/** Daraja answered with an HTTP error. Its JSON body usually names the error, e.g.
 * {"requestId":"…","errorCode":"400.002.02","errorMessage":"Bad Request - Invalid Amount"}. */
public class DarajaHttpException extends RuntimeException {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final int status;
    private final String errorCode;
    private final String errorMessage;

    public DarajaHttpException(int status, byte[] body) {
        super((status >= 500 ? "Server error: " : "Client error: ") + status);
        this.status = status;
        String code = null;
        String message = null;
        try {
            JsonNode json = body == null || body.length == 0 ? null
                    : JSON.readTree(new String(body, StandardCharsets.UTF_8));
            if (json != null && json.hasNonNull("errorCode")) {
                code = json.get("errorCode").asText();
                message = json.hasNonNull("errorMessage") ? json.get("errorMessage").asText() : null;
            }
        } catch (Exception ignored) {
            // Not JSON (a proxy's HTML page, say): only the status is known.
        }
        this.errorCode = code;
        this.errorMessage = message;
    }

    public int status() {
        return status;
    }

    /** Daraja's own error code, or null when the body did not carry one. */
    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
