package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Error response structure from M-Pesa API
 * Represents various error conditions that can occur
 */
@Schema(description = "M-Pesa API error response")
public record MpesaErrorResponse(

        @Schema(description = "Error code from Safaricom", example = "400.002.02")
        @JsonProperty("errorCode")
        String errorCode,

        @Schema(description = "Error message description", example = "Bad Request - Invalid Amount")
        @JsonProperty("errorMessage")
        String errorMessage,

        @Schema(description = "Request ID for tracking")
        @JsonProperty("requestId")
        String requestId,

        @Schema(description = "Timestamp when error occurred")
        LocalDateTime timestamp
) {

    /**
     * Common error types based on Daraja API documentation
     */
    public static class ErrorCodes {

        // Authentication errors
        public static final String INVALID_ACCESS_TOKEN = "400.002.01";
        public static final String INVALID_CREDENTIALS = "401.001.01";

        // Request validation errors
        public static final String INVALID_AMOUNT = "400.002.02";
        public static final String INVALID_PHONE_NUMBER = "400.002.03";
        public static final String INVALID_SHORTCODE = "400.002.04";
        public static final String MISSING_REQUIRED_PARAMETER = "400.001.01";

        // Business logic errors
        public static final String INSUFFICIENT_FUNDS = "400.008.01";
        public static final String DUPLICATE_REQUEST = "400.008.02";
        public static final String TRANSACTION_NOT_FOUND = "400.008.03";
        public static final String INVALID_TRANSACTION_STATE = "400.008.04";

        // System errors
        public static final String INTERNAL_SERVER_ERROR = "500.001.01";
        public static final String SERVICE_UNAVAILABLE = "503.001.01";
        public static final String TIMEOUT = "408.001.01";

        // Rate limiting
        public static final String RATE_LIMIT_EXCEEDED = "429.001.01";
    }

    /**
     * Check if error is due to authentication issues
     */
    public boolean isAuthenticationError() {
        return ErrorCodes.INVALID_ACCESS_TOKEN.equals(errorCode) ||
                ErrorCodes.INVALID_CREDENTIALS.equals(errorCode);
    }

    /**
     * Check if error is due to validation issues
     */
    public boolean isValidationError() {
        return errorCode != null && errorCode.startsWith("400.002");
    }

    /**
     * Check if error is due to business logic issues
     */
    public boolean isBusinessLogicError() {
        return errorCode != null && errorCode.startsWith("400.008");
    }

    /**
     * Check if error is a system/server error
     */
    public boolean isSystemError() {
        return errorCode != null && (errorCode.startsWith("500") || errorCode.startsWith("503"));
    }

    /**
     * Check if error is due to rate limiting
     */
    public boolean isRateLimitError() {
        return ErrorCodes.RATE_LIMIT_EXCEEDED.equals(errorCode);
    }
}
