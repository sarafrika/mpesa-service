package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of a bulk payout; outcome follows its B2C payout once it was sent. */
@Schema(name = "PayoutBatchItemDto")
public record PayoutBatchItemDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("line") int line,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Masked unless the caller has mpesa-finance")
        @JsonProperty("phone") String phone,
        @JsonProperty("name") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reference") String reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"QUEUED", "PENDING", "PAID", "FAILED", "UNKNOWN", "REFUSED", "ERROR"},
                description = "UNKNOWN: Safaricom never confirmed receiving it, so it may still be paid")
        @JsonProperty("outcome") String outcome,
        @Schema(description = "The B2C payout, a transaction in the feed")
        @JsonProperty("payout_uuid") UUID payoutUuid,
        @JsonProperty("receipt") String receipt,
        @JsonProperty("message") String message,
        @JsonProperty("processed_at") OffsetDateTime processedAt) {
}
