package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A money movement a maker raised; a checker who is not the maker approves or rejects it. */
@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @UuidGenerator
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "type", nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApprovalStatus status = ApprovalStatus.WAITING;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "shortcode_uuid")
    private UUID shortcodeUuid;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    /** What the executor needs, as JSON text. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    /** [{"state": "OK"|"WARN"|"FAIL", "text": "..."}] worked out when the request was raised. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "pre_checks", nullable = false, columnDefinition = "jsonb")
    private String preChecks;

    @Column(name = "daraja_call")
    private String darajaCall;

    @Column(name = "maker", nullable = false)
    private String maker;

    @Column(name = "maker_name")
    private String makerName;

    @Column(name = "checker")
    private String checker;

    @Column(name = "checker_name")
    private String checkerName;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    /** Refreshed while an execution runs; a stale one means the runner stopped and may be resumed. */
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "outcome_message", length = 1000)
    private String outcomeMessage;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
