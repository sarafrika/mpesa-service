package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for M-Pesa Shortcode Configuration
 *
 * This record represents the complete configuration for an M-Pesa shortcode,
 * including all necessary API credentials, URLs, and business settings.
 */
@Schema(
        name = "MpesaShortCodeDto",
        description = "M-Pesa shortcode configuration containing API credentials, URLs, and business settings"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MpesaShortCodeDto(

        @Schema(
                description = "UUID for the shortcode record used for external references",
                example = "550e8400-e29b-41d4-a716-446655440000",
                accessMode = Schema.AccessMode.READ_ONLY
        )
        @JsonProperty("uuid")
        UUID uuid,

        @Schema(
                description = "M-Pesa shortcode number (paybill or till number)",
                example = "174379",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 5,
                maxLength = 10
        )
        @NotBlank(message = "Shortcode is required")
        @Size(min = 5, max = 10, message = "Shortcode must be between 5 and 10 characters")
        @Pattern(regexp = "^[0-9]+$", message = "Shortcode must contain only numbers")
        @JsonProperty("shortcode")
        String shortcode,

        @Schema(
                description = "Type of M-Pesa shortcode",
                example = "PAYBILL",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"PAYBILL", "TILL"}
        )
        @NotNull(message = "Shortcode type is required")
        @JsonProperty("shortcode_type")
        ShortcodeType shortcodeType,

        @Schema(
                description = "Business name associated with the shortcode",
                example = "Acme Corporation",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255
        )
        @NotBlank(message = "Business name is required")
        @Size(max = 255, message = "Business name cannot exceed 255 characters")
        @JsonProperty("business_name")
        String businessName,

        @Schema(
                description = "M-Pesa API consumer key for authentication",
                example = "9v38Dtu5u2BpsITPmLcXNWGMsjZRWSTG",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255
        )
        @NotBlank(message = "Consumer key is required")
        @Size(max = 255, message = "Consumer key cannot exceed 255 characters")
        @JsonProperty("consumer_key")
        String consumerKey,

        @Schema(
                description = "M-Pesa API consumer secret for authentication (encrypted in storage)",
                example = "WVguoNWM4T4HVDVDFAYVFERG52846ncds8mavd",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 500,
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotBlank(message = "Consumer secret is required")
        @Size(max = 500, message = "Consumer secret cannot exceed 500 characters")
        @JsonProperty("consumer_secret")
        String consumerSecret,

        @Schema(
                description = "M-Pesa passkey for STK push transactions (optional for some configurations)",
                example = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919",
                maxLength = 500,
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @Size(max = 500, message = "Passkey cannot exceed 500 characters")
        @JsonProperty("passkey")
        String passkey,

        @Schema(
                description = "Callback URL for receiving transaction notifications",
                example = "https://api.example.com/mpesa/callback",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 500
        )
        @NotBlank(message = "Callback URL is required")
        @Size(max = 500, message = "Callback URL cannot exceed 500 characters")
        @Pattern(regexp = "^https?://.*", message = "Callback URL must be a valid HTTP/HTTPS URL")
        @JsonProperty("callback_url")
        String callbackUrl,

        @Schema(
                description = "Confirmation URL for C2B transaction confirmations",
                example = "https://api.example.com/mpesa/confirmation",
                maxLength = 500
        )
        @Size(max = 500, message = "Confirmation URL cannot exceed 500 characters")
        @Pattern(regexp = "^https?://.*", message = "Confirmation URL must be a valid HTTP/HTTPS URL")
        @JsonProperty("confirmation_url")
        String confirmationUrl,

        @Schema(
                description = "Validation URL for C2B transaction validations",
                example = "https://api.example.com/mpesa/validation",
                maxLength = 500
        )
        @Size(max = 500, message = "Validation URL cannot exceed 500 characters")
        @Pattern(regexp = "^https?://.*", message = "Validation URL must be a valid HTTP/HTTPS URL")
        @JsonProperty("validation_url")
        String validationUrl,

        @Schema(
                description = "Minimum transaction amount allowed",
                example = "1.00",
                minimum = "0.01"
        )
        @DecimalMin(value = "0.01", message = "Minimum amount must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "Amount must have maximum 8 integer digits and 2 decimal places")
        @JsonProperty("min_amount")
        BigDecimal minAmount,

        @Schema(
                description = "Maximum transaction amount allowed",
                example = "70000.00",
                minimum = "0.01"
        )
        @DecimalMin(value = "0.01", message = "Maximum amount must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "Amount must have maximum 8 integer digits and 2 decimal places")
        @JsonProperty("max_amount")
        BigDecimal maxAmount,

        @Schema(
                description = "Whether the shortcode configuration is active",
                example = "true"
        )
        @JsonProperty("is_active")
        Boolean isActive,

        @Schema(
                description = "M-Pesa API environment",
                example = "SANDBOX",
                allowableValues = {"SANDBOX", "PRODUCTION"}
        )
        @JsonProperty("environment")
        Environment environment,

        @Schema(
                description = "Default account reference for transactions",
                example = "ACC001",
                maxLength = 100
        )
        @Size(max = 100, message = "Account reference cannot exceed 100 characters")
        @JsonProperty("account_reference")
        String accountReference,

        @Schema(
                description = "Default transaction description",
                example = "Payment for services",
                maxLength = 255
        )
        @Size(max = 255, message = "Transaction description cannot exceed 255 characters")
        @JsonProperty("transaction_desc")
        String transactionDesc,

        @Schema(
                description = "Timestamp when the record was created",
                example = "2024-01-15T10:30:00",
                accessMode = Schema.AccessMode.READ_ONLY
        )
        @JsonProperty("created_at")
        LocalDateTime createdAt,

        @Schema(
                description = "Timestamp when the record was last updated",
                example = "2024-01-15T14:20:00",
                accessMode = Schema.AccessMode.READ_ONLY
        )
        @JsonProperty("updated_at")
        LocalDateTime updatedAt,

        @Schema(
                description = "User who created the record",
                example = "admin@example.com",
                maxLength = 100,
                accessMode = Schema.AccessMode.READ_ONLY
        )
        @JsonProperty("created_by")
        String createdBy,

        @Schema(
                description = "User who last updated the record",
                example = "admin@example.com",
                maxLength = 100,
                accessMode = Schema.AccessMode.READ_ONLY
        )
        @JsonProperty("updated_by")
        String updatedBy

) {
    /**
     * Creates a new MpesaShortCodeDto for creation requests
     * Excludes read-only fields like uuid, timestamps
     */
    public static MpesaShortCodeDto forCreation(
            String shortcode,
            ShortcodeType shortcodeType,
            String businessName,
            String consumerKey,
            String consumerSecret,
            String passkey,
            String callbackUrl,
            String confirmationUrl,
            String validationUrl,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Environment environment,
            String accountReference,
            String transactionDesc
    ) {
        return new MpesaShortCodeDto(
                null, shortcode, shortcodeType, businessName,
                consumerKey, consumerSecret, passkey, callbackUrl,
                confirmationUrl, validationUrl, minAmount, maxAmount,
                true, environment, accountReference, transactionDesc,
                null, null, null, null
        );
    }

    /**
     * Creates a new MpesaShortCodeDto for update requests
     * Includes UUID for identification but excludes other read-only fields
     */
    public static MpesaShortCodeDto forUpdate(
            UUID uuid,
            String shortcode,
            ShortcodeType shortcodeType,
            String businessName,
            String consumerKey,
            String consumerSecret,
            String passkey,
            String callbackUrl,
            String confirmationUrl,
            String validationUrl,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Boolean isActive,
            Environment environment,
            String accountReference,
            String transactionDesc
    ) {
        return new MpesaShortCodeDto(
                uuid, shortcode, shortcodeType, businessName,
                consumerKey, consumerSecret, passkey, callbackUrl,
                confirmationUrl, validationUrl, minAmount, maxAmount,
                isActive, environment, accountReference, transactionDesc,
                null, null, null, null
        );
    }

    /**
     * Validates that max amount is greater than min amount
     */
    public boolean isAmountRangeValid() {
        if (minAmount == null || maxAmount == null) {
            return true; // Let individual field validations handle null cases
        }
        return maxAmount.compareTo(minAmount) > 0;
    }

    /**
     * Checks if this shortcode is configured for STK Push
     */
    public boolean isSTKPushEnabled() {
        return passkey != null && !passkey.trim().isEmpty();
    }

    /**
     * Checks if this shortcode is configured for C2B
     */
    public boolean isC2BEnabled() {
        return confirmationUrl != null && !confirmationUrl.trim().isEmpty();
    }
}