package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.converters.OutgoingPaymentStatusConverter;
import com.sarafrika.apps.mpesaservice.utils.converters.OutgoingPaymentTypeConverter;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "mpesa_outgoing_payments")
@Getter
@Setter
@NoArgsConstructor
public class MpesaOutgoingPayment extends BaseEntity {

    @Column(name = "shortcode_uuid")
    private UUID shortcodeUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shortcode_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
    private MpesaShortCode shortcode;

    @Convert(converter = OutgoingPaymentTypeConverter.class)
    @Column(name = "payment_type")
    private OutgoingPaymentType paymentType;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "originator_conversation_id")
    private String originatorConversationId;

    @Column(name = "recipient_phone_number")
    private String recipientPhoneNumber;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "command_id")
    private String commandId;

    @Column(name = "initiator_name")
    private String initiatorName;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "occasion")
    private String occasion;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "utility_account_available_funds")
    private BigDecimal utilityAccountAvailableFunds;

    @Column(name = "working_account_available_funds")
    private BigDecimal workingAccountAvailableFunds;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Convert(converter = OutgoingPaymentStatusConverter.class)
    @Column(name = "status")
    private OutgoingPaymentStatus status = OutgoingPaymentStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_callback_data")
    private Map<String, Object> rawCallbackData;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}