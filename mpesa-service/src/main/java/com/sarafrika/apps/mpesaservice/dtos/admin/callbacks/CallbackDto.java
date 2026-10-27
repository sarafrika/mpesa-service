package com.sarafrika.apps.mpesaservice.dtos.admin.callbacks;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of the callback inbox. */
@Schema(name = "CallbackDto")
public record CallbackDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "/api/v1/callbacks/c2b/confirmation")
        @JsonProperty("endpoint") String endpoint,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "C2B_CONFIRMATION", allowableValues = {
                "C2B_VALIDATION", "C2B_CONFIRMATION", "STK_CALLBACK", "B2C_RESULT", "B2C_TIMEOUT", "B2B_RESULT",
                "B2B_TIMEOUT", "TRANSACTION_STATUS_RESULT", "TRANSACTION_STATUS_TIMEOUT", "ACCOUNT_BALANCE_RESULT",
                "ACCOUNT_BALANCE_TIMEOUT", "REVERSAL_RESULT", "REVERSAL_TIMEOUT", "OTHER"})
        @JsonProperty("kind") String kind,
        @Schema(nullable = true, example = "196.201.214.200")
        @JsonProperty("source_ip") String sourceIp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"RECEIVED", "PROCESSED", "FAILED", "REPLAYED", "REJECTED"})
        @JsonProperty("status") String status,
        @Schema(nullable = true, description = "Why processing failed, or for REJECTED why it was refused before "
                + "any handler ran; no stack traces")
        @JsonProperty("error") String error,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Times the handler ran, replays included")
        @JsonProperty("attempts") int attempts,
        @Schema(nullable = true)
        @JsonProperty("last_attempt_at") OffsetDateTime lastAttemptAt,
        @Schema(nullable = true, description = "Receipts and conversation ids found in the body")
        @JsonProperty("reference") String reference,
        @Schema(nullable = true, description = "The payment the callback is about, as /transactions?tx= opens it")
        @JsonProperty("payment_uuid") UUID paymentUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("received_at") OffsetDateTime receivedAt) {
}
