package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A shortcode's profile, limits, defaults, callback URLs, masked credentials and health. */
@Schema(name = "ShortcodeDetail")
public record ShortcodeDetailDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "600100")
        @JsonProperty("shortcode") String shortcode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika Fees")
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_type") ShortcodeType shortcodeType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("environment") Environment environment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("status") ShortcodeStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("is_active") boolean active,
        @JsonProperty("account_reference") String accountReference,
        @JsonProperty("transaction_desc") String transactionDesc,
        @JsonProperty("min_amount") BigDecimal minAmount,
        @JsonProperty("max_amount") BigDecimal maxAmount,
        @Schema(description = "Where Safaricom posts STK push results")
        @JsonProperty("callback_url") String callbackUrl,
        @Schema(description = "Last C2B confirmation URL registered with Safaricom")
        @JsonProperty("confirmation_url") String confirmationUrl,
        @JsonProperty("validation_url") String validationUrl,
        @JsonProperty("b2c_result_url") String b2cResultUrl,
        @JsonProperty("b2c_timeout_url") String b2cTimeoutUrl,
        @JsonProperty("activated_at") LocalDateTime activatedAt,
        @JsonProperty("suspended_at") LocalDateTime suspendedAt,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("updated_by") String updatedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("credentials") ShortcodeCredentialsDto credentials,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("health") ShortcodeHealthDto health) {
}
