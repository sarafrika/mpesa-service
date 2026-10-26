package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One shortcode's traffic today (Africa/Nairobi), latest float and state. */
@Schema(name = "PaybillHealthDto")
public record PaybillHealthDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "600100")
        @JsonProperty("shortcode") String shortcode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika Fees")
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PAYBILL", allowableValues = {"PAYBILL", "TILL"})
        @JsonProperty("shortcode_type") String shortcodeType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PRODUCTION",
                allowableValues = {"SANDBOX", "PRODUCTION"})
        @JsonProperty("environment") String environment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("active") boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("state") PaybillState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "902140.00")
        @JsonProperty("collected_today_amount") BigDecimal collectedTodayAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2400")
        @JsonProperty("collected_today_count") long collectedTodayCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0.00")
        @JsonProperty("paid_out_today_amount") BigDecimal paidOutTodayAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
        @JsonProperty("paid_out_today_count") long paidOutTodayCount,
        @Schema(nullable = true, description = "Newest payment or callback on this shortcode; null when none yet")
        @JsonProperty("last_activity_at") Instant lastActivityAt,
        @Schema(nullable = true, example = "38420.00",
                description = "Utility balance last reported by an Account Balance result or a settled payout")
        @JsonProperty("float_amount") BigDecimal floatAmount,
        @Schema(nullable = true)
        @JsonProperty("float_reported_at") Instant floatReportedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0",
                description = "Payments in either direction that failed in the last hour")
        @JsonProperty("failed_last_hour") long failedLastHour,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "40",
                description = "Payments in either direction that settled in the last hour")
        @JsonProperty("settled_last_hour") long settledLastHour,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "50000.00",
                description = "Low-float line: the shortcode's own threshold, else mpesa.alerts.low-float")
        @JsonProperty("low_float_threshold") BigDecimal lowFloatThreshold) {
}
