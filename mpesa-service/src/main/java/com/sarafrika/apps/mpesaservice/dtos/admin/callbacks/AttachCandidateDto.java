package com.sarafrika.apps.mpesaservice.dtos.admin.callbacks;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An open B2B transfer, reversal, Tax Remittance or Pochi payment a failed result could be attached to. */
@Schema(name = "AttachCandidateDto")
public record AttachCandidateDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"B2B_TRANSFER", "REVERSAL", "TAX_REMITTANCE",
                "BUSINESS_TO_POCHI"})
        @JsonProperty("kind") String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Sweep SWP-7K2Q9XHD to 522522")
        @JsonProperty("label") String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PENDING")
        @JsonProperty("status") String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("outcome_unknown") boolean outcomeUnknown,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The result's amount and receiver (or original receipt) agree with it")
        @JsonProperty("fits") boolean fits,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") OffsetDateTime createdAt) {
}
