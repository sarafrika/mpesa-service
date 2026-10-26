package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A bulk payout and how far its payouts have got. */
@Schema(name = "PayoutBatchDto")
public record PayoutBatchDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("approval_uuid") UUID approvalUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @JsonProperty("shortcode") String shortcode,
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("title") String title,
        @JsonProperty("file_name") String fileName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"WAITING", "REJECTED", "EXPIRED", "RUNNING", "COMPLETED"})
        @JsonProperty("status") String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("item_count") int itemCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Rows left out of the file as invalid")
        @JsonProperty("invalid_count") int invalidCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Items handed to the payout service so far")
        @JsonProperty("processed_count") int processedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("counts") Counts counts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("paid_amount") BigDecimal paidAmount,
        @JsonProperty("started_at") OffsetDateTime startedAt,
        @JsonProperty("finished_at") OffsetDateTime finishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") OffsetDateTime createdAt) {

    /** Items by outcome; unknown ones may still be paid and are never sent again. */
    @Schema(name = "PayoutBatchCounts")
    public record Counts(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("queued") int queued,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("pending") int pending,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("paid") int paid,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("failed") int failed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("unknown") int unknown,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("refused") int refused) {
    }
}
