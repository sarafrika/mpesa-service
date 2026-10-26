package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.StatusQueryMethod;
import com.sarafrika.apps.mpesaservice.utils.enums.StatusQueryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

/** One status query sent to Safaricom for a payment, and what came back. */
@Entity
@Table(name = "transaction_status_queries")
@Getter
@Setter
@NoArgsConstructor
public class TransactionStatusQuery extends BaseEntity {

    public static final String IN = "IN";
    public static final String OUT = "OUT";

    @Column(name = "payment_uuid", nullable = false)
    private UUID paymentUuid;

    /** IN for mpesa_incoming_payments, OUT for mpesa_outgoing_payments. */
    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "shortcode_uuid")
    private UUID shortcodeUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private StatusQueryMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusQueryStatus status = StatusQueryStatus.PENDING;

    @Column(name = "queried_transaction_id")
    private String queriedTransactionId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "originator_conversation_id")
    private String originatorConversationId;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    /** Safaricom's TransactionStatus result parameter, e.g. Completed. */
    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "receipt")
    private String receipt;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw_result", columnDefinition = "jsonb")
    private String rawResult;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
