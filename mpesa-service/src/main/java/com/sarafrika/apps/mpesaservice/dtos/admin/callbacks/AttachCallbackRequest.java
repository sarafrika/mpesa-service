package com.sarafrika.apps.mpesaservice.dtos.admin.callbacks;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(name = "AttachCallbackRequest")
public record AttachCallbackRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The B2B transfer or reversal (uuid from the candidates list) the result belongs to")
        @NotNull(message = "is required")
        @JsonProperty("target_uuid") UUID targetUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Statement shows the 27 Oct sweep to 522522",
                description = "Why this result belongs to it; kept in the audit trail")
        @NotBlank(message = "is required")
        @Size(max = 500)
        @JsonProperty("note") String note) {
}
