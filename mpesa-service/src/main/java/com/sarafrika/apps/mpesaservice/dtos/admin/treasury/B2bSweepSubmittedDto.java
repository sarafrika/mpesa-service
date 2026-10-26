package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/** The sweep is waiting in Approvals; nothing has been sent to Safaricom yet. */
@Schema(name = "B2bSweepSubmittedDto")
public record B2bSweepSubmittedDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("approval_request_uuid") UUID approvalRequestUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SWP-7K2Q9XHD")
        @JsonProperty("reference") String reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "500000")
        @JsonProperty("amount") BigDecimal amount) {
}
