package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Counts for the sidebar badge and the Waiting tab. */
@Schema(name = "ApprovalSummaryDto")
public record ApprovalSummaryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
        @JsonProperty("waiting") long waiting,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1",
                description = "Waiting requests the caller raised, which someone else must decide")
        @JsonProperty("waiting_raised_by_you") long waitingRaisedByYou) {
}
