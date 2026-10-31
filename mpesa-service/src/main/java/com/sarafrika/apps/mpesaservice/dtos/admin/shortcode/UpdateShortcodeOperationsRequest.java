package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The full set of operations to leave on; anything not listed is turned off. */
@Schema(name = "UpdateShortcodeOperationsRequest")
public record UpdateShortcodeOperationsRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Must be within available_operations")
        @NotNull(message = "is required")
        @JsonProperty("operations") List<ShortcodeOperation> operations) {
}
