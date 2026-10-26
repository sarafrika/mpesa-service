package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationRunStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationSource;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** One reconciliation of a shortcode's ledger against Safaricom's record for [periodStart, periodEnd). */
@Entity
@Table(name = "reconciliation_runs")
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @UuidGenerator
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ReconciliationSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private ReconciliationTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReconciliationRunStatus status;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "statement_lines", nullable = false)
    private int statementLines;

    /** Lines outside the period, not completed, or without a receipt. */
    @Column(name = "skipped_lines", nullable = false)
    private int skippedLines;

    @Column(name = "charge_lines", nullable = false)
    private int chargeLines;

    @Column(name = "reversal_lines", nullable = false)
    private int reversalLines;

    @Column(name = "ledger_records", nullable = false)
    private int ledgerRecords;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "matched_value", nullable = false)
    private BigDecimal matchedValue = BigDecimal.ZERO;

    @Column(name = "exception_count", nullable = false)
    private int exceptionCount;

    @Column(name = "exception_value", nullable = false)
    private BigDecimal exceptionValue = BigDecimal.ZERO;

    @Column(name = "paid_in_total", nullable = false)
    private BigDecimal paidInTotal = BigDecimal.ZERO;

    @Column(name = "withdrawn_total", nullable = false)
    private BigDecimal withdrawnTotal = BigDecimal.ZERO;

    @Column(name = "charges_total", nullable = false)
    private BigDecimal chargesTotal = BigDecimal.ZERO;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
