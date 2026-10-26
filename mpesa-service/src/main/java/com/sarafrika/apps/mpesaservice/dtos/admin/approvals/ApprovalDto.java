package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A maker-checker request as the console shows it; own_request tells the caller they raised it. */
@Schema(name = "ApprovalDto")
public record ApprovalDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "REVERSAL",
                description = "REVERSAL, BULK_PAYOUT, B2B_SWEEP, TAX_REMITTANCE or BUSINESS_TO_POCHI")
        @JsonProperty("type") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"WAITING", "APPROVED", "REJECTED", "EXPIRED", "EXECUTING", "EXECUTED", "FAILED"})
        @JsonProperty("status") String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Reverse TJQ4K2L1MX")
        @JsonProperty("title") String title,
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @JsonProperty("shortcode") String shortcode,
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "15000.00")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reason") String reason,
        @Schema(description = "The Daraja request approving it sends",
                example = "POST /mpesa/reversal/v1/request · TransactionReversal")
        @JsonProperty("daraja_call") String darajaCall,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(schema = @Schema(implementation = PreCheckDto.class))
        @JsonProperty("pre_checks") List<PreCheckDto> preChecks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("maker") String maker,
        @JsonProperty("maker_name") String makerName,
        @JsonProperty("checker") String checker,
        @JsonProperty("checker_name") String checkerName,
        @JsonProperty("decision_note") String decisionNote,
        @JsonProperty("decided_at") OffsetDateTime decidedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("expires_at") OffsetDateTime expiresAt,
        @JsonProperty("executed_at") OffsetDateTime executedAt,
        @Schema(description = "What happened when it ran, or what Safaricom answered later")
        @JsonProperty("outcome_message") String outcomeMessage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The caller raised this request, so "
                + "they cannot approve or reject it")
        @JsonProperty("own_request") boolean ownRequest,
        @Schema(description = "REVERSAL: the payment being reversed")
        @JsonProperty("transaction_uuid") UUID transactionUuid,
        @Schema(description = "BULK_PAYOUT: the batch of payouts")
        @JsonProperty("batch_uuid") UUID batchUuid,
        @Schema(description = "REVERSAL: where the reversal stands at Safaricom")
        @JsonProperty("reversal") ReversalDto reversal,
        @Schema(implementation = Object.class, type = "object", nullable = true,
                additionalProperties = Schema.AdditionalPropertiesValue.TRUE, description = "The request's own fields (e.g. a B2B_SWEEP's destination and float), "
                + "on the single-request read only; phones and names masked unless finance or admin")
        @JsonProperty("payload") JsonNode payload) {
}
