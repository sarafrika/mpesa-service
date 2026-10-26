package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.ReversalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

/** A Daraja reversal of one received payment, behind one REVERSAL approval. */
@Entity
@Table(name = "reversal_requests")
@Getter
@Setter
@NoArgsConstructor
public class ReversalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @UuidGenerator
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "approval_uuid", nullable = false)
    private UUID approvalUuid;

    @Column(name = "payment_uuid", nullable = false)
    private UUID paymentUuid;

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    /** The M-Pesa receipt of the payment being reversed. */
    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "original_amount", nullable = false)
    private BigDecimal originalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReversalStatus status = ReversalStatus.WAITING_APPROVAL;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "originator_conversation_id")
    private String originatorConversationId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "result_code", length = 20)
    private String resultCode;

    @Column(name = "result_desc", length = 500)
    private String resultDesc;

    /** The receipt Safaricom gives the reversal itself. */
    @Column(name = "reversal_transaction_id", length = 50)
    private String reversalTransactionId;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw_callback", columnDefinition = "jsonb")
    private String rawCallback;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "timed_out_at")
    private Instant timedOutAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Set when finance closed it as FAILED by hand after checking the statement. */
    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "close_note", length = 500)
    private String closeNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
