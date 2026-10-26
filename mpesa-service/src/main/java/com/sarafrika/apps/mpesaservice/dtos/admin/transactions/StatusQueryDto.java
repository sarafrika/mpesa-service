package com.sarafrika.apps.mpesaservice.dtos.admin.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.TransactionStatusQuery;
import com.sarafrika.apps.mpesaservice.utils.Times;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A status query sent to Safaricom; PENDING until the Transaction Status result arrives. */
@Schema(name = "StatusQueryDto")
public record StatusQueryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"TRANSACTION_STATUS", "STK_QUERY"})
        @JsonProperty("method") String method,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"PENDING", "COMPLETED", "FAILED", "TIMED_OUT"})
        @JsonProperty("status") String status,
        @JsonProperty("result_code") Integer resultCode,
        @JsonProperty("result_desc") String resultDesc,
        @Schema(description = "Safaricom's TransactionStatus, e.g. Completed", example = "Completed")
        @JsonProperty("transaction_status") String transactionStatus,
        @JsonProperty("receipt") String receipt,
        @JsonProperty("requested_by") String requestedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("requested_at") OffsetDateTime requestedAt,
        @JsonProperty("completed_at") OffsetDateTime completedAt) {

    public static StatusQueryDto from(TransactionStatusQuery query) {
        return new StatusQueryDto(query.getUuid(), query.getMethod().name(), query.getStatus().name(),
                query.getResultCode(), query.getResultDesc(), query.getTransactionStatus(), query.getReceipt(),
                query.getCreatedBy(), Times.offset(query.getCreatedAt()), Times.offset(query.getCompletedAt()));
    }
}
