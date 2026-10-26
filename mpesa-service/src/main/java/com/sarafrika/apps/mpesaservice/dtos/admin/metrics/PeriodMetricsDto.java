package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Money in and out over one period; settled means SUCCESS, FAILED or CANCELLED, in either direction. */
@Schema(name = "PeriodMetricsDto")
public record PeriodMetricsDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1284350.00",
                description = "Sum of successful incoming payments")
        @JsonProperty("collected_amount") BigDecimal collectedAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3412")
        @JsonProperty("collected_count") long collectedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Successful incoming payments per payment type (STK_PUSH, C2B, B2B_EXPRESS_CHECKOUT), zero when none")
        @JsonProperty("collected_by_type") List<TypeTotalDto> collectedByType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "412900.00",
                description = "Sum of successful outgoing payments")
        @JsonProperty("payouts_amount") BigDecimal payoutsAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "186")
        @JsonProperty("payouts_count") long payoutsCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3700")
        @JsonProperty("settled_count") long settledCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3598")
        @JsonProperty("successful_count") long successfulCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "71")
        @JsonProperty("failed_count") long failedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "31")
        @JsonProperty("cancelled_count") long cancelledCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
        @JsonProperty("pending_count") long pendingCount,
        @Schema(example = "0.9724", nullable = true,
                description = "successful_count / settled_count between 0 and 1; null when nothing settled")
        @JsonProperty("success_rate") Double successRate) {
}
