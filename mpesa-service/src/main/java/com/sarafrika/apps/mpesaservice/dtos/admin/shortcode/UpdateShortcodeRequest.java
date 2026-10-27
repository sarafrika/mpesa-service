package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Profile, limits and defaults to change; a field left out (null) keeps its value, "" clears an optional one. */
@Schema(name = "UpdateShortcodeRequest")
public record UpdateShortcodeRequest(
        @Size(max = 255, message = "must be at most 255 characters")
        @Pattern(regexp = "[\\s\\S]*\\S[\\s\\S]*", message = "must not be blank")
        @JsonProperty("business_name") String businessName,
        @DecimalMin(value = "1", message = "must be at least 1")
        @Digits(integer = 8, fraction = 2, message = "must have at most 8 digits and 2 decimals")
        @JsonProperty("min_amount") BigDecimal minAmount,
        @DecimalMin(value = "1", message = "must be at least 1")
        @Digits(integer = 8, fraction = 2, message = "must have at most 8 digits and 2 decimals")
        @JsonProperty("max_amount") BigDecimal maxAmount,
        @Size(max = 100, message = "must be at most 100 characters")
        @JsonProperty("account_reference") String accountReference,
        @Size(max = 255, message = "must be at most 255 characters")
        @JsonProperty("transaction_desc") String transactionDesc,
        @Schema(description = "Override where Safaricom posts STK results; \"\" restores the default")
        @Size(max = 500, message = "must be at most 500 characters")
        @Pattern(regexp = "^$|^https://.+", message = "must be an https URL")
        @JsonProperty("callback_url") String callbackUrl,
        @Size(max = 500, message = "must be at most 500 characters")
        @Pattern(regexp = "^$|^https://.+", message = "must be an https URL")
        @JsonProperty("b2c_result_url") String b2cResultUrl,
        @Size(max = 500, message = "must be at most 500 characters")
        @Pattern(regexp = "^$|^https://.+", message = "must be an https URL")
        @JsonProperty("b2c_timeout_url") String b2cTimeoutUrl,
        @Schema(description = "A till's store/HO number; sending it (even unchanged) confirms it. Tills only")
        @Pattern(regexp = "^[0-9]{5,10}$", message = "must be 5 to 10 digits")
        @JsonProperty("store_number") String storeNumber) {

    public UpdateShortcodeRequest(String businessName, BigDecimal minAmount, BigDecimal maxAmount,
                                  String accountReference, String transactionDesc, String callbackUrl,
                                  String b2cResultUrl, String b2cTimeoutUrl) {
        this(businessName, minAmount, maxAmount, accountReference, transactionDesc, callbackUrl, b2cResultUrl,
                b2cTimeoutUrl, null);
    }
}
