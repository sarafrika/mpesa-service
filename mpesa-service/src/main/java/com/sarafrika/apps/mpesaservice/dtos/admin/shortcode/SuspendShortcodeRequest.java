package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Why the shortcode is being suspended; kept in the audit trail. */
@Schema(name = "SuspendShortcodeRequest")
public record SuspendShortcodeRequest(
        @Schema(example = "Credentials leaked; rotating")
        @Size(max = 500, message = "must be at most 500 characters")
        @JsonProperty("reason") String reason) {
}
