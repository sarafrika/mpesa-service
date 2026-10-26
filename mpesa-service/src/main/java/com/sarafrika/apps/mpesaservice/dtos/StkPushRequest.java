package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload to initiate an STK Push (Lipa na M-Pesa Online) against any
 * configured shortcode. The shortcode is resolved by UUID, keeping the gateway
 * generic across the full network of tills and paybills.
 */
@Schema(description = "Request to initiate an STK Push payment")
public record StkPushRequest(

        @Schema(description = "UUID of the configured shortcode to charge", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid")
        UUID shortcodeUuid,

        @Schema(description = "Customer phone number in 254XXXXXXXXX format", example = "254708374149",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("phone_number")
        String phoneNumber,

        @Schema(description = "Amount to charge", example = "100.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("amount")
        BigDecimal amount,

        @Schema(description = "Account reference shown to the customer", example = "INV-001")
        @JsonProperty("account_reference")
        String accountReference,

        @Schema(description = "Short transaction description", example = "Payment for order 001")
        @JsonProperty("transaction_desc")
        String transactionDesc
) {}
