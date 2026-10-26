package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "ApproveApprovalRequest")
public record ApproveApprovalRequest(
        @Schema(description = "Optional note to the maker")
        @Size(max = 1000)
        @JsonProperty("note") String note) {
}
