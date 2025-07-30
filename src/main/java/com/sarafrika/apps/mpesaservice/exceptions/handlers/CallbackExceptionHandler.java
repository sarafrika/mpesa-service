package com.sarafrika.apps.mpesaservice.exceptions.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;

/**
 * Global exception handler for M-Pesa callback endpoints
 * <p>
 * This handler ensures that callback endpoints always return proper responses
 * to M-Pesa, even when errors occur, to prevent callback retries
 * </p>
 */
@RestControllerAdvice(basePackages = "com.sarafrika.apps.mpesaservice.controllers.callbacks")
@Slf4j
public class CallbackExceptionHandler {

    /**
     * Handle JSON parsing errors in callback requests
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CallbackErrorResponse> handleJsonParsingError(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.error("JSON parsing error in callback endpoint {}: {}", request.getRequestURI(), ex.getMessage());

        // Return success to M-Pesa to prevent retries, but log the error internally
        return ResponseEntity.ok(new CallbackErrorResponse(
                "00000001",
                "JSON parsing error",
                LocalDateTime.now()
        ));
    }

    /**
     * Handle method argument type mismatch errors
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CallbackErrorResponse> handleTypeMismatchError(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        log.error("Type mismatch error in callback endpoint {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.ok(new CallbackErrorResponse(
                "00000001",
                "Invalid parameter type",
                LocalDateTime.now()
        ));
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CallbackErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.error("Illegal argument error in callback endpoint {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.ok(new CallbackErrorResponse(
                "00000001",
                "Invalid argument provided",
                LocalDateTime.now()
        ));
    }

    /**
     * Handle null pointer exceptions
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<CallbackErrorResponse> handleNullPointer(
            NullPointerException ex, HttpServletRequest request) {

        log.error("Null pointer error in callback endpoint {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.ok(new CallbackErrorResponse(
                "00000001",
                "Internal processing error",
                LocalDateTime.now()
        ));
    }

    /**
     * Handle all other runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<CallbackErrorResponse> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {

        log.error("Runtime error in callback endpoint {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.ok(new CallbackErrorResponse(
                "00000001",
                "Runtime processing error",
                LocalDateTime.now()
        ));
    }

    /**
     * Handle all other exceptions as fallback
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CallbackErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error in callback endpoint {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.ok(new CallbackErrorResponse(
                "00000001",
                "Unexpected server error",
                LocalDateTime.now()
        ));
    }

    /**
     * Standard error response for callback endpoints
     * Always returns HTTP 200 to prevent M-Pesa retries
     */
    public record CallbackErrorResponse(
            @JsonProperty("ResultCode")
            String resultCode,

            @JsonProperty("ResultDesc")
            String resultDesc,

            @JsonProperty("Timestamp")
            LocalDateTime timestamp
    ) {}
}