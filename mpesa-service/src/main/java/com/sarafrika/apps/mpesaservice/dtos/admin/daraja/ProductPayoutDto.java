package com.sarafrika.apps.mpesaservice.dtos.admin.daraja;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.ProductPayout;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.ProductPayoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An approved Tax Remittance or Pochi payment sent to Safaricom and where it stands. */
@Schema(name = "ProductPayoutDto")
public record ProductPayoutDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"TAX_REMITTANCE", "BUSINESS_TO_POCHI"})
        @JsonProperty("product") DarajaFeature product,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("approval_request_uuid") UUID approvalRequestUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "572572 (KRA) for tax; the wallet MSISDN for Pochi, masked unless finance or admin")
        @JsonProperty("party_b") String partyB,
        @Schema(description = "The KRA PRN for tax; null for Pochi")
        @JsonProperty("account_reference") String accountReference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("remarks") String remarks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("status") ProductPayoutStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Safaricom never confirmed the request; it may still move. Never resend it.")
        @JsonProperty("outcome_unknown") boolean outcomeUnknown,
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("result_code") String resultCode,
        @JsonProperty("result_desc") String resultDesc,
        @Schema(description = "ReceiverPartyPublicName from the result; masked for Pochi unless finance or admin")
        @JsonProperty("receiver_name") String receiverName,
        @JsonProperty("maker") String maker,
        @JsonProperty("checker") String checker,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("timed_out_at") OffsetDateTime timedOutAt,
        @JsonProperty("completed_at") OffsetDateTime completedAt,
        @Schema(description = "Who closed it as failed by hand after checking the statement")
        @JsonProperty("closed_by") String closedBy,
        @JsonProperty("close_note") String closeNote) {

    public static ProductPayoutDto from(ProductPayout p, boolean showPersonalData) {
        boolean pochi = p.getProduct() == DarajaFeature.BUSINESS_TO_POCHI;
        boolean mask = pochi && !showPersonalData;
        return new ProductPayoutDto(p.getUuid(), p.getShortcodeUuid(), p.getProduct(), p.getApprovalRequestUuid(),
                mask ? PersonalData.maskPhone(p.getPartyB()) : p.getPartyB(), p.getAccountReference(), p.getAmount(),
                p.getRemarks(), p.getStatus(), p.isOutcomeUnknown(), p.getTransactionId(), p.getResultCode(),
                p.getResultDesc(), mask ? PersonalData.maskPublicName(p.getReceiverName()) : p.getReceiverName(), p.getMaker(),
                p.getChecker(), Times.offset(p.getCreatedAt()), Times.offset(p.getTimedOutAt()),
                Times.offset(p.getCompletedAt()), p.getClosedBy(), p.getCloseNote());
    }
}
