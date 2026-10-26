package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationAction;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionKind;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionStatus;
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
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A receipt a reconciliation run could not match, with the suggested fix and how a person resolved it. */
@Entity
@Table(name = "reconciliation_exceptions")
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationExceptionRecord {

    public static final String IN = "IN";
    public static final String OUT = "OUT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @UuidGenerator
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ReconciliationExceptionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReconciliationExceptionStatus status = ReconciliationExceptionStatus.OPEN;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "receipt", nullable = false)
    private String receipt;

    @Column(name = "statement_amount")
    private BigDecimal statementAmount;

    @Column(name = "ledger_amount")
    private BigDecimal ledgerAmount;

    @Column(name = "ledger_count", nullable = false)
    private int ledgerCount;

    @Column(name = "ledger_payment_uuid")
    private UUID ledgerPaymentUuid;

    @Column(name = "ledger_status")
    private String ledgerStatus;

    /** C2B, STK_PUSH, B2C, B2B or B2B_TRANSFER (a treasury sweep, which has no status query here). */
    @Column(name = "ledger_type")
    private String ledgerType;

    @Column(name = "statement_time")
    private LocalDateTime statementTime;

    @Column(name = "details")
    private String details;

    @Column(name = "account_reference")
    private String accountReference;

    /** Safaricom's "phone - name" for the other party; masked on the way out unless finance or admin. */
    @Column(name = "other_party")
    private String otherParty;

    /** Receipt of the statement line that reversed this one. */
    @Column(name = "reversed_by")
    private String reversedBy;

    @Column(name = "suggested_fix", nullable = false)
    private String suggestedFix;

    /** The statement or Pull Transactions line as read, so a backfill needs nothing else. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "statement_line", columnDefinition = "jsonb")
    private String statementLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_action")
    private ReconciliationAction lastAction;

    @Column(name = "status_query_uuid")
    private UUID statusQueryUuid;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
