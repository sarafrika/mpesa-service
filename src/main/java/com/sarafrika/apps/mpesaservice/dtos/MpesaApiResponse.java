package com.sarafrika.apps.mpesaservice.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Generic wrapper for all M-Pesa API responses
 * Provides consistent structure for success and error handling
 */
@Schema(description = "Generic M-Pesa API response wrapper")
public record MpesaApiResponse<T>(

        @Schema(description = "Indicates if the request was successful")
        boolean success,

        @Schema(description = "Response data (null if error)")
        T data,

        @Schema(description = "Error details (null if successful)")
        MpesaErrorResponse error,

        @Schema(description = "Response timestamp")
        LocalDateTime timestamp,

        @Schema(description = "HTTP status code")
        int httpStatus,

        @Schema(description = "Request processing duration in milliseconds")
        Long processingTimeMs
) {

    /**
     * Create successful response
     */
    public static <T> MpesaApiResponse<T> success(T data, int httpStatus) {
        return new MpesaApiResponse<>(true, data, null, LocalDateTime.now(), httpStatus, null);
    }

    /**
     * Create successful response with processing time
     */
    public static <T> MpesaApiResponse<T> success(T data, int httpStatus, long processingTimeMs) {
        return new MpesaApiResponse<>(true, data, null, LocalDateTime.now(), httpStatus, processingTimeMs);
    }

    /**
     * Create error response
     */
    public static <T> MpesaApiResponse<T> error(MpesaErrorResponse error, int httpStatus) {
        return new MpesaApiResponse<>(false, null, error, LocalDateTime.now(), httpStatus, null);
    }

    /**
     * Create error response with processing time
     */
    public static <T> MpesaApiResponse<T> error(MpesaErrorResponse error, int httpStatus, long processingTimeMs) {
        return new MpesaApiResponse<>(false, null, error, LocalDateTime.now(), httpStatus, processingTimeMs);
    }

    /**
     * Create error response from exception
     */
    public static <T> MpesaApiResponse<T> error(String errorCode, String errorMessage, int httpStatus) {
        MpesaErrorResponse errorResponse = new MpesaErrorResponse(
                errorCode,
                errorMessage,
                null,
                LocalDateTime.now()
        );
        return error(errorResponse, httpStatus);
    }

    /**
     * Create error response from exception with processing time
     */
    public static <T> MpesaApiResponse<T> error(String errorCode, String errorMessage, int httpStatus, long processingTimeMs) {
        MpesaErrorResponse errorResponse = new MpesaErrorResponse(
                errorCode,
                errorMessage,
                null,
                LocalDateTime.now()
        );
        return error(errorResponse, httpStatus, processingTimeMs);
    }
}