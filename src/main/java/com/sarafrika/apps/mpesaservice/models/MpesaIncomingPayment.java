package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.converters.IncomingPaymentStatusConverter;
import com.sarafrika.apps.mpesaservice.utils.converters.IncomingPaymentTypeConverter;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
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
@Table(name = "mpesa_incoming_payments")
@Getter
@Setter
@NoArgsConstructor
public class MpesaIncomingPayment extends BaseEntity {

    @Column(name = "shortcode_uuid")
    private UUID shortcodeUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shortcode_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
    private MpesaShortCode shortcode;

    @Convert(converter = IncomingPaymentTypeConverter.class)
    @Column(name = "payment_type")
    private IncomingPaymentType paymentType;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "checkout_request_id")
    private String checkoutRequestId;

    @Column(name = "merchant_request_id")
    private String merchantRequestId;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "account_reference")
    private String accountReference;

    @Column(name = "transaction_desc")
    private String transactionDesc;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Convert(converter = IncomingPaymentStatusConverter.class)
    @Column(name = "status")
    private IncomingPaymentStatus status = IncomingPaymentStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_callback_data")
    private Map<String, Object> rawCallbackData;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}