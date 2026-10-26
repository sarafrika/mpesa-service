package com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ResolveReconciliationExceptionRequest")
public record ResolveReconciliationExceptionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Customer refunded by cheque, ref 4471")
        @NotBlank(message = "Say why this is resolved")
        @Size(max = 1000, message = "must be at most 1000 characters")
        @JsonProperty("note") String note) {
}
