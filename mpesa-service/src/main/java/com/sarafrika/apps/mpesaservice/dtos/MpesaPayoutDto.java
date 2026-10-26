package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "A B2C payout and what Safaricom said about it")
public record MpesaPayoutDto(
        @JsonProperty("uuid") UUID uuid,
        @JsonProperty("client_reference") String clientReference,
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(description = "PENDING until Safaricom's result arrives, then SUCCESS or FAILED. FAILED in the "
                + "send response means Daraja refused it; a later success result can still turn it SUCCESS")
        @JsonProperty("status") OutgoingPaymentStatus status,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("phone_number") String phoneNumber,
        @Schema(description = "M-Pesa receipt, e.g. TJE4K9L2QX")
        @JsonProperty("transaction_id") String transactionId,
        @Schema(description = "Registered name M-Pesa returned for the recipient")
        @JsonProperty("recipient_name") String recipientName,
        @JsonProperty("charges_paid") BigDecimal chargesPaid,
        @JsonProperty("result_code") Integer resultCode,
        @JsonProperty("result_desc") String resultDesc,
        @Schema(description = "Set when Safaricom reported a queue timeout; the outcome is still unknown")
        @JsonProperty("timed_out_at") LocalDateTime timedOutAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "true while PENDING when Safaricom never "
                + "confirmed receiving the payout: it may still be paid, so wait for the result and do not retry")
        @JsonProperty("outcome_unknown") boolean outcomeUnknown,
        @JsonProperty("transaction_date") LocalDateTime transactionDate,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("processed_at") LocalDateTime processedAt
) {

    public static MpesaPayoutDto from(MpesaOutgoingPayment payment) {
        return new MpesaPayoutDto(payment.getUuid(), payment.getClientReference(), payment.getShortcodeUuid(),
                payment.getStatus(), payment.getAmount(), payment.getRecipientPhoneNumber(), payment.getTransactionId(),
                payment.getRecipientName(), payment.getChargesPaid(), payment.getResultCode(), payment.getResultDesc(),
                payment.getTimedOutAt(), payment.isOutcomeUnknown(), payment.getTransactionDate(), payment.getCreatedAt(), payment.getProcessedAt());
    }
}
