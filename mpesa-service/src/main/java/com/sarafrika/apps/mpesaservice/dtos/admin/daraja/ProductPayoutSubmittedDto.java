package com.sarafrika.apps.mpesaservice.dtos.admin.daraja;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/** A Tax Remittance or Pochi payment waiting in Approvals; nothing is sent until a checker approves it. */
@Schema(name = "ProductPayoutSubmittedDto")
public record ProductPayoutSubmittedDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("approval_request_uuid") UUID approvalRequestUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("amount") BigDecimal amount) {
}
