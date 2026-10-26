package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A reversal of a received payment and Safaricom's answer to it. */
@Schema(name = "ReversalDto")
public record ReversalDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("transaction_uuid") UUID transactionUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TJQ4K2L1MX",
                description = "Receipt of the payment being reversed")
        @JsonProperty("receipt") String receipt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("original_amount") BigDecimal originalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"WAITING_APPROVAL", "CANCELLED",
                "SENDING", "SENT", "OUTCOME_UNKNOWN", "REVERSED", "FAILED"})
        @JsonProperty("status") String status,
        @JsonProperty("result_code") String resultCode,
        @JsonProperty("result_desc") String resultDesc,
        @Schema(description = "Safaricom's receipt for the reversal itself")
        @JsonProperty("reversal_receipt") String reversalReceipt,
        @JsonProperty("sent_at") OffsetDateTime sentAt,
        @JsonProperty("completed_at") OffsetDateTime completedAt) {
}
