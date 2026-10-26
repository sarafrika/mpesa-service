package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Response from STK Push status query
 * Contains the final transaction details after customer completes payment
 */
@Schema(description = "STK Push status query response from Daraja API")
public record StkStatusResponse(

        @Schema(description = "Merchant request ID", example = "21605-295434-4")
        @JsonProperty("MerchantRequestID")
        String merchantRequestId,

        @Schema(description = "Checkout request ID", example = "ws_CO_04112017184930742")
        @JsonProperty("CheckoutRequestID")
        String checkoutRequestId,

        @Schema(description = "Result code (0 = success, 1 = insufficient funds, 1032 = cancelled by user)", example = "0")
        @JsonProperty("ResultCode")
        Integer resultCode,

        @Schema(description = "Result description", example = "The service request is processed successfully.")
        @JsonProperty("ResultDesc")
        String resultDesc,

        @Schema(description = "Transaction amount", example = "1.00")
        @JsonProperty("Amount")
        BigDecimal amount,

        @Schema(description = "M-Pesa receipt number", example = "LK451H35OP")
        @JsonProperty("MpesaReceiptNumber")
        String mpesaReceiptNumber,

        @Schema(description = "Transaction date in format YYYYMMDDHHMMSS", example = "20171104184944")
        @JsonProperty("TransactionDate")
        Long transactionDate,

        @Schema(description = "Customer phone number", example = "254727894083")
        @JsonProperty("PhoneNumber")
        Long phoneNumber
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