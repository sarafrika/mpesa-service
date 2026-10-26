package com.sarafrika.apps.mpesaservice.dtos.admin.features;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Switches a flagged Daraja product on or off for one shortcode. */
@Schema(name = "UpdateShortcodeFeatureRequest")
public record UpdateShortcodeFeatureRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "is required")
        @JsonProperty("enabled") Boolean enabled) {
}
