package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A shortcode's float as last known, the low-float line it is held to and the newest balance query. */
@Schema(name = "ShortcodeFloatStatusDto")
public record ShortcodeFloatStatusDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(nullable = true, description = "Newest completed Account Balance result; null when none yet")
        @JsonProperty("latest") BalanceSnapshotDto latest,
        @Schema(nullable = true, description = "Newest balance query of any status, e.g. one still PENDING")
        @JsonProperty("last_query") BalanceSnapshotDto lastQuery,
        @Schema(nullable = true, example = "1640210.00",
                description = "Utility balance from the newest report: a balance result, a settled B2C payout or a C2B "
                        + "payment's OrgAccountBalance")
        @JsonProperty("utility_available") BigDecimal utilityAvailable,
        @Schema(nullable = true)
        @JsonProperty("utility_reported_at") OffsetDateTime utilityReportedAt,
        @Schema(nullable = true, allowableValues = {"BALANCE_QUERY", "PAYOUT", "C2B_PAYMENT"},
                description = "Where utility_available came from")
        @JsonProperty("utility_source") String utilitySource,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "50000.00",
                description = "The line this shortcode is held to: its own, else the service default")
        @JsonProperty("low_float_threshold") BigDecimal lowFloatThreshold,
        @Schema(nullable = true, description = "This shortcode's own threshold; null uses the default")
        @JsonProperty("low_float_threshold_override") BigDecimal lowFloatThresholdOverride,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "50000.00",
                description = "mpesa.alerts.low-float")
        @JsonProperty("default_low_float_threshold") BigDecimal defaultLowFloatThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Utility balance known and below low_float_threshold")
        @JsonProperty("low_float") boolean lowFloat,
        @Schema(nullable = true, example = "30",
                description = "Minutes between scheduled balance queries; null when they are off")
        @JsonProperty("auto_query_interval_minutes") Long autoQueryIntervalMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The shortcode has an initiator, which Account Balance and B2B need")
        @JsonProperty("initiator_configured") boolean initiatorConfigured) {
}
