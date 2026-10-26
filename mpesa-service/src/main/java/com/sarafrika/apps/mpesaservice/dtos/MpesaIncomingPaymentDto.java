package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read model for an incoming M-Pesa payment. Exposes the fields consuming systems
 * need to poll and reconcile a payment without leaking the JPA entity or its raw
 * callback payload.
 */
@Schema(description = "Incoming M-Pesa payment status view")
public record MpesaIncomingPaymentDto(

        @JsonProperty("uuid")
        UUID uuid,

        @JsonProperty("shortcode_uuid")
        UUID shortcodeUuid,

        @JsonProperty("payment_type")
        IncomingPaymentType paymentType,

        @JsonProperty("transaction_id")
        String transactionId,

        @JsonProperty("checkout_request_id")
        String checkoutRequestId,

        @JsonProperty("merchant_request_id")
        String merchantRequestId,

        @JsonProperty("phone_number")
        String phoneNumber,

        @JsonProperty("amount")
        BigDecimal amount,

        @JsonProperty("account_reference")
        String accountReference,

        @JsonProperty("transaction_desc")
        String transactionDesc,

        @JsonProperty("result_code")
        Integer resultCode,

        @JsonProperty("result_desc")
        String resultDesc,

        @JsonProperty("status")
        IncomingPaymentStatus status,

        @JsonProperty("transaction_date")
        LocalDateTime transactionDate,

        @JsonProperty("processed_at")
        LocalDateTime processedAt,

        @JsonProperty("created_at")
        LocalDateTime createdAt
) {

    public static MpesaIncomingPaymentDto from(MpesaIncomingPayment payment) {
        return new MpesaIncomingPaymentDto(
                payment.getUuid(),
                payment.getShortcodeUuid(),
                payment.getPaymentType(),
                payment.getTransactionId(),
                payment.getCheckoutRequestId(),
                payment.getMerchantRequestId(),
                payment.getPhoneNumber(),
                payment.getAmount(),
                payment.getAccountReference(),
                payment.getTransactionDesc(),
                payment.getResultCode(),
                payment.getResultDesc(),
                payment.getStatus(),
                payment.getTransactionDate(),
                payment.getProcessedAt(),
                payment.getCreatedAt()
        );
    }
}
