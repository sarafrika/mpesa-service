package com.sarafrika.apps.mpesaservice.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller for handling STK Push callbacks from M-Pesa Daraja API
 * <p>
 * M-Pesa sends callbacks to the CallBackURL specified in STK Push requests
 * This controller processes payment confirmations and updates transaction status
 * </p>
 */
@RestController
@RequestMapping("/api/v1/callbacks/stk")
@RequiredArgsConstructor
@Slf4j
public class STKCallbackController {

    // TODO: Inject transaction processing service
    // private final TransactionProcessingService transactionProcessingService;

    /**
     * Handle STK Push callback from M-Pesa
     * This endpoint receives payment confirmation or failure notifications
     *
     * @param callbackPayload The callback payload from M-Pesa
     * @return Acknowledgment response
     */
    @PostMapping("/callback")
    public ResponseEntity<STKCallbackAcknowledgment> handleSTKCallback(
            @RequestBody STKCallbackPayload callbackPayload) {

        log.info("Received STK Push callback for MerchantRequestID: {}, CheckoutRequestID: {}",
                callbackPayload.body().stkCallback().merchantRequestId(),
                callbackPayload.body().stkCallback().checkoutRequestId());

        try {
            // Extract callback data
            STKCallback stkCallback = callbackPayload.body().stkCallback();

            // Log the callback details
            log.info("STK Callback Details - ResultCode: {}, ResultDesc: {}",
                    stkCallback.resultCode(), stkCallback.resultDesc());

            // Process based on result code
            if (stkCallback.resultCode() == 0) {
                // Payment successful
                handleSuccessfulPayment(stkCallback);
            } else {
                // Payment failed or cancelled
                handleFailedPayment(stkCallback);
            }

            // Return acknowledgment
            return ResponseEntity.ok(new STKCallbackAcknowledgment(
                    "00000000",
                    "success",
                    LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("Error processing STK Push callback", e);

            // Still return success to M-Pesa to avoid retries
            return ResponseEntity.ok(new STKCallbackAcknowledgment(
                    "00000001",
                    "Internal server error",
                    LocalDateTime.now()
            ));
        }
    }

    /**
     * Handle successful STK Push payment
     */
    private void handleSuccessfulPayment(STKCallback callback) {
        log.info("Processing successful STK Push payment for CheckoutRequestID: {}",
                callback.checkoutRequestId());

        // Extract payment details from callback metadata
        CallbackMetadata metadata = callback.callbackMetadata();
        if (metadata != null && metadata.items() != null) {

            BigDecimal amount = null;
            String mpesaReceiptNumber = null;
            String phoneNumber = null;
            Long transactionDate = null;

            // Extract values from callback metadata items
            for (CallbackMetadataItem item : metadata.items()) {
                switch (item.name()) {
                    case "Amount" -> amount = item.value() != null ? new BigDecimal(item.value().toString()) : null;
                    case "MpesaReceiptNumber" -> mpesaReceiptNumber = item.value() != null ? item.value().toString() : null;
                    case "PhoneNumber" -> phoneNumber = item.value() != null ? item.value().toString() : null;
                    case "TransactionDate" -> transactionDate = item.value() != null ? Long.valueOf(item.value().toString()) : null;
                }
            }

            log.info("Payment Details - Amount: {}, Receipt: {}, Phone: {}, Date: {}",
                    amount, mpesaReceiptNumber, phoneNumber, transactionDate);

            // TODO: Update transaction in database
            // TODO: Notify customer of successful payment
            // TODO: Trigger any business logic (order fulfillment, etc.)
        }
    }

    /**
     * Handle failed or cancelled STK Push payment
     */
    private void handleFailedPayment(STKCallback callback) {
        log.warn("Processing failed STK Push payment for CheckoutRequestID: {} - Reason: {}",
                callback.checkoutRequestId(), callback.resultDesc());

        // TODO: Update transaction status to failed
        // TODO: Notify customer of failed payment
        // TODO: Handle retry logic if applicable
    }

    // ==================== CALLBACK DATA STRUCTURES ====================

    /**
     * Main STK Push callback payload structure
     */
    public record STKCallbackPayload(
            @JsonProperty("Body")
            STKCallbackBody body
    ) {}

    /**
     * STK Push callback body
     */
    public record STKCallbackBody(
            @JsonProperty("stkCallback")
            STKCallback stkCallback
    ) {}

    /**
     * STK Push callback details
     */
    public record STKCallback(
            @JsonProperty("MerchantRequestID")
            String merchantRequestId,

            @JsonProperty("CheckoutRequestID")
            String checkoutRequestId,

            @JsonProperty("ResultCode")
            Integer resultCode,

            @JsonProperty("ResultDesc")
            String resultDesc,

            @JsonProperty("CallbackMetadata")
            CallbackMetadata callbackMetadata
    ) {

        /**
         * Check if the transaction was successful
         */
        public boolean isSuccessful() {
            return resultCode != null && resultCode == 0;
        }

        /**
         * Check if transaction was cancelled by user
         */
        public boolean wasCancelled() {
            return resultCode != null && resultCode == 1032;
        }

        /**
         * Check if user had insufficient funds
         */
        public boolean hasInsufficientFunds() {
            return resultCode != null && resultCode == 1;
        }
    }

    /**
     * STK Push callback metadata containing transaction details
     */
    public record CallbackMetadata(
            @JsonProperty("Item")
            List<CallbackMetadataItem> items
    ) {}

    /**
     * Individual metadata item
     */
    public record CallbackMetadataItem(
            @JsonProperty("Name")
            String name,

            @JsonProperty("Value")
            Object value
    ) {}

    /**
     * Acknowledgment response to send back to M-Pesa
     */
    public record STKCallbackAcknowledgment(
            @JsonProperty("ResultCode")
            String resultCode,

            @JsonProperty("ResultDesc")
            String resultDesc,

            @JsonProperty("ThirdPartyTransID")
            LocalDateTime timestamp
    ) {}
}