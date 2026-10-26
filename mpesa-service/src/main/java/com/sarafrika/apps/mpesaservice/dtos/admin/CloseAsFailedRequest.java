package com.sarafrika.apps.mpesaservice.dtos.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CloseAsFailedRequest")
public record CloseAsFailedRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                example = "M-Pesa statement for 27-28 Oct shows no debit for this amount",
                description = "What the statement showed; kept with the record and in the audit trail")
        @NotBlank(message = "is required: say what the statement showed")
        @Size(max = 500)
        @JsonProperty("note") String note) {
}
