package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A shortcode's utility balance as the newest Account Balance result or settled payout reported it. */
@Schema(name = "ShortcodeFloatDto")
public record ShortcodeFloatDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "600200")
        @JsonProperty("shortcode") String shortcode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika Payouts")
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "38420.00")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(nullable = true)
        @JsonProperty("reported_at") Instant reportedAt) {
}
