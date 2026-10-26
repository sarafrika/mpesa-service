package com.sarafrika.apps.mpesaservice.dtos.admin.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Everything known about one transaction: the feed row, the fields only its own table has, timeline and raw callback. */
@Schema(name = "TransactionDetailDto")
public record TransactionDetailDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("transaction") TransactionDto transaction,
        @JsonProperty("merchant_request_id") String merchantRequestId,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("middle_name") String middleName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("invoice_number") String invoiceNumber,
        @Schema(description = "The paybill's balance after a C2B payment, as Safaricom reported it")
        @JsonProperty("org_account_balance") BigDecimal orgAccountBalance,
        @JsonProperty("transaction_desc") String transactionDesc,
        @JsonProperty("command_id") String commandId,
        @JsonProperty("initiator_name") String initiatorName,
        @JsonProperty("remarks") String remarks,
        @JsonProperty("occasion") String occasion,
        @JsonProperty("charges_paid") BigDecimal chargesPaid,
        @JsonProperty("utility_account_available_funds") BigDecimal utilityAccountAvailableFunds,
        @JsonProperty("working_account_available_funds") BigDecimal workingAccountAvailableFunds,
        @Schema(description = "Set when Safaricom reported a queue timeout; the outcome is then still unknown")
        @JsonProperty("timed_out_at") OffsetDateTime timedOutAt,
        @Schema(implementation = Object.class, type = "object", nullable = true,
                additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
                description = "Safaricom's last callback for it, exactly as received")
        @JsonProperty("raw_callback") JsonNode rawCallback,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(schema = @Schema(implementation = TimelineEventDto.class))
        @JsonProperty("timeline") List<TimelineEventDto> timeline,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(schema = @Schema(implementation = StatusQueryDto.class))
        @JsonProperty("status_queries") List<StatusQueryDto> statusQueries,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether Safaricom can be asked for its status (a known shortcode, and a receipt, "
                        + "checkout id or conversation id to ask about)")
        @JsonProperty("can_query_status") boolean canQueryStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "A pending payout Safaricom never "
                + "confirmed receiving: it may still be paid, so it must not be sent again")
        @JsonProperty("outcome_unknown") boolean outcomeUnknown,
        @Schema(description = "What to do about a known failure, e.g. B2C ResultCode 2001 (invalid initiator)")
        @JsonProperty("hint") String hint) {
}
