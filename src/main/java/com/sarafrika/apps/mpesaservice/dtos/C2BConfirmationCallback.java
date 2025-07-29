package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * C2B confirmation payload
 * Received on confirmation URL after successful payment
 */
@Schema(description = "C2B confirmation callback from Daraja API")
public record C2BConfirmationCallback(

        @Schema(description = "Transaction type", example = "Pay Bill")
        @JsonProperty("TransType")
        String transType,

        @Schema(description = "Transaction ID", example = "LHG31AA5TX")
        @JsonProperty("TransID")
        String transId,

        @Schema(description = "Transaction time in format YYYYMMDDHHMMSS", example = "20230727104247")
        @JsonProperty("TransTime")
        String transTime,

        @Schema(description = "Transaction amount", example = "100.00")
        @JsonProperty("TransAmount")
        BigDecimal transAmount,

        @Schema(description = "Business shortcode that received payment", example = "600134")
        @JsonProperty("BusinessShortCode")
        String businessShortCode,

        @Schema(description = "Bill reference number/account number", example = "account001")
        @JsonProperty("BillRefNumber")
        String billRefNumber,

        @Schema(description = "Invoice number if applicable")
        @JsonProperty("InvoiceNumber")
        String invoiceNumber,

        @Schema(description = "Organization account balance after transaction")
        @JsonProperty("OrgAccountBalance")
        BigDecimal orgAccountBalance,

        @Schema(description = "Third party transaction ID")
        @JsonProperty("ThirdPartyTransID")
        String thirdPartyTransId,

        @Schema(description = "Customer phone number", example = "254708374149")
        @JsonProperty("MSISDN")
        String msisdn,

        @Schema(description = "Customer first name", example = "John")
        @JsonProperty("FirstName")
        String firstName,

        @Schema(description = "Customer middle name", example = "J")
        @JsonProperty("MiddleName")
        String middleName,

        @Schema(description = "Customer last name", example = "Doe")
        @JsonProperty("LastName")
        String lastName
) {}