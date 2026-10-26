package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** The URLs themselves come from this service's callback configuration. */
@Schema(name = "RegisterC2bUrlsRequest")
public record RegisterC2bUrlsRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Cancelled rejects payments when validation is unreachable; Completed accepts them")
        @NotNull(message = "is required")
        @JsonProperty("response_type") C2bResponseType responseType) {
}
