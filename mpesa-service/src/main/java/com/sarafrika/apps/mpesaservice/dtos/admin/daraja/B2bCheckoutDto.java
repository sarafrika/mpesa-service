package com.sarafrika.apps.mpesaservice.dtos.admin.daraja;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.B2bCheckoutRequest;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bCheckoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A B2B Express Checkout push and where it stands. */
@Schema(name = "B2bCheckoutDto")
public record B2bCheckoutDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Our RequestRefID, Safaricom's requestId")
        @JsonProperty("request_ref_id") UUID requestRefId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("payer_till") String payerTill,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("receiver_short_code") String receiverShortCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("payment_ref") String paymentRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("partner_name") String partnerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("status") B2bCheckoutStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Safaricom never confirmed the push; the merchant may still pay. Do not push again.")
        @JsonProperty("outcome_unknown") boolean outcomeUnknown,
        @JsonProperty("result_code") String resultCode,
        @JsonProperty("result_desc") String resultDesc,
        @JsonProperty("transaction_id") String transactionId,
        @Schema(description = "The incoming payment a successful checkout created")
        @JsonProperty("incoming_payment_uuid") UUID incomingPaymentUuid,
        @JsonProperty("requested_by") String requestedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("completed_at") OffsetDateTime completedAt) {

    public static B2bCheckoutDto from(B2bCheckoutRequest c) {
        return new B2bCheckoutDto(c.getUuid(), c.getShortcodeUuid(), c.getRequestRefId(), c.getPrimaryShortCode(),
                c.getReceiverShortCode(), c.getAmount(), c.getPaymentRef(), c.getPartnerName(), c.getStatus(),
                c.isOutcomeUnknown(), c.getResultCode(), c.getResultDesc(), c.getTransactionId(),
                c.getIncomingPaymentUuid(), c.getCreatedBy(), Times.offset(c.getCreatedAt()),
                Times.offset(c.getCompletedAt()));
    }
}
