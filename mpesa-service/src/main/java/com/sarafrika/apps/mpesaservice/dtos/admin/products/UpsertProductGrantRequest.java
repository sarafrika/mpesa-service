package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Creates or replaces a product's grant on one shortcode. */
public record UpsertProductGrantRequest(
        @Schema(example = "Elimika", description = "Renames the product on all its grants when given")
        @Size(max = 255)
        @JsonProperty("display_name") String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "choose at least one operation")
        @JsonProperty("operations") Set<@NotNull GrantOperation> operations,
        @Schema(description = "Defaults to true; false pauses the grant without deleting it")
        @JsonProperty("active") Boolean active) {
}
