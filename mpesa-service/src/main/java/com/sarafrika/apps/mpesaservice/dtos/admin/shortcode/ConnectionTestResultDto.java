package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Whether Daraja issued an OAuth token for the shortcode's credentials, and how fast. */
@Schema(name = "ConnectionTestResult")
public record ConnectionTestResultDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("ok") boolean ok,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "312")
        @JsonProperty("latency_ms") long latencyMs,
        @Schema(example = "Daraja refused these credentials (401)", description = "Why it failed; null when ok")
        @JsonProperty("error") String error,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("checked_at") LocalDateTime checkedAt) {
}
