package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** An approved B2B sweep or B2C top-up sent to Safaricom; its result callback settles it once. */
@Entity
@Table(name = "b2b_transfers")
@SQLDelete(sql = "UPDATE b2b_transfers SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class B2bTransfer extends BaseEntity {

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Column(name = "destination_uuid", nullable = false)
    private UUID destinationUuid;

    @Column(name = "approval_request_uuid", nullable = false)
    private UUID approvalRequestUuid;

    @Column(name = "command_id", nullable = false)
    private String commandId;

    @Column(name = "receiver_shortcode", nullable = false)
    private String receiverShortcode;

    @Column(name = "account_reference")
    private String accountReference;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "remarks")
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private B2bTransferStatus status = B2bTransferStatus.PENDING;

    /** Safaricom never confirmed it took the request (no reply, or a queue timeout): it may still move. */
    @Column(name = "outcome_unknown", nullable = false)
    private boolean outcomeUnknown;

    @Column(name = "originator_conversation_id")
    private String originatorConversationId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    /** DebitPartyCharges from the result; null when Safaricom left it empty. */
    @Column(name = "charges")
    private BigDecimal charges;

    /** TransCompletedTime from the result (Nairobi wall clock, stored as an instant). */
    @Column(name = "trans_completed_at")
    private Instant transCompletedAt;

    /** The Working account's available balance after the debit (DebitPartyAffectedAccountBalance). */
    @Column(name = "working_balance_after")
    private BigDecimal workingBalanceAfter;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw_result", columnDefinition = "jsonb")
    private String rawResult;

    @Column(name = "maker")
    private String maker;

    @Column(name = "checker")
    private String checker;

    @Column(name = "timed_out_at")
    private Instant timedOutAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Set when finance closed it as FAILED by hand after checking the statement. */
    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "close_note")
    private String closeNote;
}
