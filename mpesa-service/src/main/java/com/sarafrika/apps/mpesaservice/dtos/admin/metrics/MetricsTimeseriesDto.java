package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "MetricsTimeseriesDto")
public record MetricsTimeseriesDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("from") Instant from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("to") Instant to,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"hour", "day"})
        @JsonProperty("bucket") String bucket,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Africa/Nairobi")
        @JsonProperty("time_zone") String timeZone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Oldest bucket first")
        @JsonProperty("points") List<TimeseriesPointDto> points) {
}
