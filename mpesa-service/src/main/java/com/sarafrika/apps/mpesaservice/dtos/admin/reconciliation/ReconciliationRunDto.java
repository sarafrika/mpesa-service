package com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.ReconciliationRun;
import com.sarafrika.apps.mpesaservice.utils.Times;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** One reconciliation run with its counts and values; match_rate is matched / (matched + exceptions) in percent. */
@Schema(name = "ReconciliationRunDto")
public record ReconciliationRunDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @JsonProperty("shortcode") String shortcode,
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"STATEMENT", "PULL_TRANSACTIONS"})
        @JsonProperty("source") String source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"MANUAL", "SCHEDULED"})
        @JsonProperty("trigger") String trigger,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"COMPLETED", "FAILED"})
        @JsonProperty("status") String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("period_start") OffsetDateTime periodStart,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Exclusive end of the period")
        @JsonProperty("period_end") OffsetDateTime periodEnd,
        @JsonProperty("file_name") String fileName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("statement_lines") int statementLines,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Lines outside the period, not completed, or internal transfers")
        @JsonProperty("skipped_lines") int skippedLines,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("charge_lines") int chargeLines,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("reversal_lines") int reversalLines,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("ledger_records") int ledgerRecords,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("matched_count") int matchedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("matched_value") BigDecimal matchedValue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("exception_count") int exceptionCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("exception_value") BigDecimal exceptionValue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("open_exceptions") long openExceptions,
        @Schema(description = "Percent matched by count; null when nothing was compared", example = "99.62")
        @JsonProperty("match_rate") BigDecimal matchRate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("paid_in_total") BigDecimal paidInTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("withdrawn_total") BigDecimal withdrawnTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("charges_total") BigDecimal chargesTotal,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("created_by") String createdBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("completed_at") OffsetDateTime completedAt) {

    public static ReconciliationRunDto from(ReconciliationRun run, String shortcode, String businessName,
                                            long openExceptions) {
        int compared = run.getMatchedCount() + run.getExceptionCount();
        BigDecimal rate = compared == 0 ? null : BigDecimal.valueOf(run.getMatchedCount() * 100L)
                .divide(BigDecimal.valueOf(compared), 2, RoundingMode.DOWN);
        return new ReconciliationRunDto(run.getUuid(), run.getShortcodeUuid(), shortcode, businessName,
                run.getSource().name(), run.getTrigger().name(), run.getStatus().name(),
                nairobi(run.getPeriodStart()), nairobi(run.getPeriodEnd()), run.getFileName(),
                run.getStatementLines(), run.getSkippedLines(), run.getChargeLines(), run.getReversalLines(),
                run.getLedgerRecords(), run.getMatchedCount(), run.getMatchedValue(), run.getExceptionCount(),
                run.getExceptionValue(), openExceptions, rate, run.getPaidInTotal(), run.getWithdrawnTotal(),
                run.getChargesTotal(), run.getFailureReason(), run.getCreatedBy(), Times.offset(run.getCreatedAt()),
                Times.offset(run.getCompletedAt()));
    }

    /** Periods are East Africa Time, like statement lines and Daraja's TransTime. */
    public static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    public static OffsetDateTime nairobi(LocalDateTime value) {
        return value == null ? null : value.atZone(NAIROBI).toOffsetDateTime();
    }
}
