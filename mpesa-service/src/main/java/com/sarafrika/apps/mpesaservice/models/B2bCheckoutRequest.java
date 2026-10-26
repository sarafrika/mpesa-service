package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.B2bCheckoutStatus;
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

/** A B2B Express Checkout USSD push to a merchant's till; its callback settles it once. */
@Entity
@Table(name = "b2b_checkout_requests")
@SQLDelete(sql = "UPDATE b2b_checkout_requests SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class B2bCheckoutRequest extends BaseEntity {

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Column(name = "request_ref_id", nullable = false)
    private UUID requestRefId;

    /** The paying merchant's till (debit party). */
    @Column(name = "primary_short_code", nullable = false)
    private String primaryShortCode;

    /** Our paybill (credit party). */
    @Column(name = "receiver_short_code", nullable = false)
    private String receiverShortCode;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_ref", nullable = false)
    private String paymentRef;

    @Column(name = "partner_name", nullable = false)
    private String partnerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private B2bCheckoutStatus status = B2bCheckoutStatus.PENDING;

    @Column(name = "outcome_unknown", nullable = false)
    private boolean outcomeUnknown;

    @Column(name = "result_code")
    private String resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "incoming_payment_uuid")
    private UUID incomingPaymentUuid;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw_result", columnDefinition = "jsonb")
    private String rawResult;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
