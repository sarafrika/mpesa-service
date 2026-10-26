package com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.ReconciliationExceptionRecord;
import com.sarafrika.apps.mpesaservice.utils.Times;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A receipt a run could not match; other_party, details and account_reference are masked unless the caller has
 * mpesa-finance or mpesa-admin. */
@Schema(name = "ReconciliationExceptionDto")
public record ReconciliationExceptionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("run_uuid") UUID runUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"MISSING_IN_LEDGER", "MISSING_IN_STATEMENT", "AMOUNT_MISMATCH", "DUPLICATE_IN_LEDGER"})
        @JsonProperty("kind") String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"OPEN", "RESOLVED", "SUPERSEDED"})
        @JsonProperty("status") String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"in", "out"})
        @JsonProperty("direction") String direction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TJP9X2K1AA") @JsonProperty("receipt") String receipt,
        @Schema(description = "Amount on Safaricom's record; null when it has no such line")
        @JsonProperty("statement_amount") BigDecimal statementAmount,
        @Schema(description = "Amount in our ledger (the first row for a duplicate); null when we have none")
        @JsonProperty("ledger_amount") BigDecimal ledgerAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("ledger_count") int ledgerCount,
        @Schema(description = "Our transaction, for its detail page") @JsonProperty("ledger_payment_uuid") UUID ledgerPaymentUuid,
        @JsonProperty("ledger_status") String ledgerStatus,
        @JsonProperty("ledger_type") String ledgerType,
        @JsonProperty("statement_time") OffsetDateTime statementTime,
        @Schema(description = "Phone numbers and names masked unless mpesa-finance or mpesa-admin")
        @JsonProperty("details") String details,
        @Schema(description = "Masked like a phone when it is one, unless mpesa-finance or mpesa-admin")
        @JsonProperty("account_reference") String accountReference,
        @JsonProperty("other_party") String otherParty,
        @Schema(description = "Receipt of the line that reversed this one at Safaricom")
        @JsonProperty("reversed_by") String reversedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("suggested_fix") String suggestedFix,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Settled STK payments without a receipt this deposit may be, when the run stored it")
        @JsonProperty("stk_candidates") int stkCandidates,
        @ArraySchema(schema = @Schema(allowableValues = {"BACKFILL", "ATTACH_STK_RECEIPT", "QUERY_STATUS",
                "MERGE_DUPLICATE", "MARK_RESOLVED"}),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "What a person may do now; empty once resolved"))
        @JsonProperty("allowed_actions") List<String> allowedActions,
        @JsonProperty("last_action") String lastAction,
        @JsonProperty("status_query_uuid") UUID statusQueryUuid,
        @JsonProperty("resolution_note") String resolutionNote,
        @JsonProperty("resolved_by") String resolvedBy,
        @JsonProperty("resolved_at") OffsetDateTime resolvedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("created_at") OffsetDateTime createdAt) {

    public static ReconciliationExceptionDto from(ReconciliationExceptionRecord e, UUID runUuid, List<String> actions,
                                                  String otherParty, String details, String accountReference) {
        return new ReconciliationExceptionDto(e.getUuid(), runUuid, e.getKind().name(), e.getStatus().name(),
                e.getDirection().toLowerCase(java.util.Locale.ROOT), e.getReceipt(), e.getStatementAmount(),
                e.getLedgerAmount(), e.getLedgerCount(), e.getLedgerPaymentUuid(), e.getLedgerStatus(),
                e.getLedgerType(), ReconciliationRunDto.nairobi(e.getStatementTime()), details,
                accountReference, otherParty, e.getReversedBy(), e.getSuggestedFix(), e.getStkCandidates(), actions,
                e.getLastAction() == null ? null : e.getLastAction().name(), e.getStatusQueryUuid(),
                e.getResolutionNote(), e.getResolvedBy(), Times.offset(e.getResolvedAt()), Times.offset(e.getCreatedAt()));
    }
}
