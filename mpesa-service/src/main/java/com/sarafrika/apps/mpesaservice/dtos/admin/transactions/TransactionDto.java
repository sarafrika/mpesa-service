package com.sarafrika.apps.mpesaservice.dtos.admin.transactions;

import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of the transactions feed: money in (C2B, STK push) or money out (B2C, B2B). */
@Schema(name = "TransactionDto")
public record TransactionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"in", "out"})
        @JsonProperty("direction") String direction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"C2B", "STK_PUSH", "B2C", "B2B"})
        @JsonProperty("type") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"PENDING", "SUCCESS", "FAILED", "CANCELLED"})
        @JsonProperty("status") String status,
        @Schema(description = "Null for a C2B payment to a shortcode not configured here")
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(example = "600100")
        @JsonProperty("shortcode") String shortcode,
        @Schema(example = "Elimika Fees")
        @JsonProperty("business_name") String businessName,
        @Schema(description = "M-Pesa receipt; null until Safaricom issues one", example = "TJQ4K2L9XA")
        @JsonProperty("receipt") String receipt,
        @Schema(example = "ws_CO_26102026143140")
        @JsonProperty("checkout_request_id") String checkoutRequestId,
        @Schema(example = "AG_20261026_0000a91c")
        @JsonProperty("conversation_id") String conversationId,
        @JsonProperty("originator_conversation_id") String originatorConversationId,
        @Schema(description = "BillRefNumber or STK account reference; the client reference for payouts",
                example = "ELM-004512")
        @JsonProperty("account_reference") String accountReference,
        @Schema(description = "Payer or recipient MSISDN as Safaricom gave it", example = "254712345612")
        @JsonProperty("phone") String phone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4500.00")
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("result_code") Integer resultCode,
        @JsonProperty("result_desc") String resultDesc,
        @Schema(description = "The product (Keycloak azp) that asked for it", example = "elimika-backend")
        @JsonProperty("client_id") String clientId,
        @Schema(example = "JANE W KAMAU")
        @JsonProperty("customer_name") String customerName,
        @Schema(allowableValues = {"PAYBILL", "BUY_GOODS"})
        @JsonProperty("c2b_channel") String c2bChannel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("processed_at") OffsetDateTime processedAt,
        @Schema(description = "When M-Pesa says it happened")
        @JsonProperty("transaction_date") OffsetDateTime transactionDate) {

    /** The same row with the phone masked and the customer's name shortened. */
    public TransactionDto masked() {
        return new TransactionDto(uuid, direction, type, status, shortcodeUuid, shortcode, businessName, receipt,
                checkoutRequestId, conversationId, originatorConversationId, accountReference,
                PersonalData.maskPhone(phone), amount, resultCode, resultDesc, clientId,
                PersonalData.shortName(customerName), c2bChannel, createdAt, processedAt, transactionDate);
    }
}
