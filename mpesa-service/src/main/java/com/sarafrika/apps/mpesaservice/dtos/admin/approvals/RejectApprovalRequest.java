package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RejectApprovalRequest")
public record RejectApprovalRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Why it was rejected, for the maker")
        @NotBlank(message = "is required to reject")
        @Size(max = 1000)
        @JsonProperty("note") String note) {
}
