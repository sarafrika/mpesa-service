package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Key figures for [from, to) and for the period of equal length just before it. */
@Schema(name = "MetricsSummaryDto")
public record MetricsSummaryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("from") Instant from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("to") Instant to,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("previous_from") Instant previousFrom,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("previous_to") Instant previousTo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("current") PeriodMetricsDto current,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("previous") PeriodMetricsDto previous,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("float") FloatSummaryDto floatSummary) {
}
