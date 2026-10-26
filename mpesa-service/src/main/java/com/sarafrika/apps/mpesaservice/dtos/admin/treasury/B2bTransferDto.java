package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.B2bTransfer;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An approved sweep sent to Safaricom and where it stands. */
@Schema(name = "B2bTransferDto")
public record B2bTransferDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("destination_uuid") UUID destinationUuid,
        @Schema(nullable = true, description = "Null when the destination was deleted since")
        @JsonProperty("destination_name") String destinationName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("approval_request_uuid") UUID approvalRequestUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"BusinessPayBill", "BusinessBuyGoods"})
        @JsonProperty("command_id") String commandId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "522522")
        @JsonProperty("receiver_shortcode") String receiverShortcode,
        @JsonProperty("account_reference") String accountReference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "500000.00")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SWP-7K2Q9XHD")
        @JsonProperty("reference") String reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("status") B2bTransferStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Safaricom never confirmed the request; it may still move. Never resend it.")
        @JsonProperty("outcome_unknown") boolean outcomeUnknown,
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("result_code") Integer resultCode,
        @JsonProperty("result_desc") String resultDesc,
        @JsonProperty("maker") String maker,
        @JsonProperty("checker") String checker,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("timed_out_at") OffsetDateTime timedOutAt,
        @JsonProperty("completed_at") OffsetDateTime completedAt) {

    public static B2bTransferDto from(B2bTransfer t, String destinationName) {
        return new B2bTransferDto(t.getUuid(), t.getShortcodeUuid(), t.getDestinationUuid(), destinationName,
                t.getApprovalRequestUuid(), t.getCommandId(), t.getReceiverShortcode(), t.getAccountReference(),
                t.getAmount(), t.getReference(), t.getStatus(), t.isOutcomeUnknown(), t.getTransactionId(),
                t.getResultCode(), t.getResultDesc(), t.getMaker(), t.getChecker(), Times.offset(t.getCreatedAt()),
                Times.offset(t.getTimedOutAt()), Times.offset(t.getCompletedAt()));
    }
}
