package com.sarafrika.apps.mpesaservice.dtos.admin.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** One step in a transaction's life, oldest first; tone colours its dot. */
@Schema(name = "TimelineEventDto")
public record TimelineEventDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "result_received")
        @JsonProperty("kind") String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Result callback: Success")
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("at") OffsetDateTime at,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"success", "warning", "danger", "info", "muted"})
        @JsonProperty("tone") String tone) {
}
