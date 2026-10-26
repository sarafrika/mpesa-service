package com.sarafrika.apps.mpesaservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/** A row of the mpesa_transaction_feed view: an incoming or outgoing payment, keyed by its uuid. */
// @Subselect keeps Hibernate from creating or validating a table for the view.
@Entity
@Immutable
@Subselect("SELECT * FROM mpesa_transaction_feed")
@Synchronize({"mpesa_incoming_payments", "mpesa_outgoing_payments", "mpesa_shortcodes"})
@Getter
@NoArgsConstructor
public class TransactionFeedEntry {

    @Id
    @Column(name = "id")
    private UUID id;

    /** IN or OUT. */
    @Column(name = "direction")
    private String direction;

    /** C2B, STK_PUSH, B2C or B2B. */
    @Column(name = "type")
    private String type;

    @Column(name = "status")
    private String status;

    @Column(name = "shortcode_uuid")
    private UUID shortcodeUuid;

    @Column(name = "shortcode")
    private String shortcode;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "checkout_request_id")
    private String checkoutRequestId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "originator_conversation_id")
    private String originatorConversationId;

    @Column(name = "account_reference")
    private String accountReference;

    @Column(name = "phone")
    private String phone;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "c2b_channel")
    private String c2bChannel;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;
}
