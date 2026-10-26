package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.ProductPayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** An approved Tax Remittance or Business to Pochi payment sent to Safaricom; its result settles it once. */
@Entity
@Table(name = "product_payouts")
@SQLDelete(sql = "UPDATE product_payouts SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class ProductPayout extends BaseEntity {

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "product", nullable = false)
    private DarajaFeature product;

    @Column(name = "approval_request_uuid", nullable = false)
    private UUID approvalRequestUuid;

    @Column(name = "command_id", nullable = false)
    private String commandId;

    /** 572572 (KRA) for tax, the recipient's MSISDN for Pochi. */
    @Column(name = "party_b", nullable = false)
    private String partyB;

    /** The KRA payment registration number (PRN) for tax; null for Pochi. */
    @Column(name = "account_reference")
    private String accountReference;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "remarks", nullable = false)
    private String remarks;

    @Column(name = "occasion")
    private String occasion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductPayoutStatus status = ProductPayoutStatus.PENDING;

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
    private String resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    @Column(name = "receiver_name")
    private String receiverName;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw_result", columnDefinition = "jsonb")
    private String rawResult;

    @Column(name = "maker")
    private String maker;

    @Column(name = "checker")
    private String checker;

    @Column(name = "timed_out_at")
    private LocalDateTime timedOutAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "close_note")
    private String closeNote;
}
