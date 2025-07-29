package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.converters.EnvironmentConverter;
import com.sarafrika.apps.mpesaservice.utils.converters.ShortcodeTypeConverter;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "mpesa_shortcodes")
@Getter
@Setter
@NoArgsConstructor
public class MpesaShortCode extends BaseEntity {

    @Column(name = "shortcode")
    private String shortcode;

    @Convert(converter = ShortcodeTypeConverter.class)
    @Column(name = "shortcode_type")
    private ShortcodeType shortcodeType;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "consumer_key")
    private String consumerKey;

    @Column(name = "consumer_secret")
    private String consumerSecret;

    @Column(name = "passkey")
    private String passkey;

    @Column(name = "callback_url")
    private String callbackUrl;

    @Column(name = "confirmation_url")
    private String confirmationUrl;

    @Column(name = "validation_url")
    private String validationUrl;

    @Column(name = "min_amount")
    private BigDecimal minAmount = BigDecimal.valueOf(1.00);

    @Column(name = "max_amount")
    private BigDecimal maxAmount = BigDecimal.valueOf(70000.00);

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Convert(converter = EnvironmentConverter.class)
    @Column(name = "environment")
    private Environment environment = Environment.SANDBOX;

    @Column(name = "account_reference")
    private String accountReference;

    @Column(name = "transaction_desc")
    private String transactionDesc = "Payment";
}