package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.converters.EncryptedSecretConverter;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "mpesa_shortcodes")
@Getter
@Setter
@NoArgsConstructor
/** Daraja credentials are encrypted at rest by {@link EncryptedSecretConverter}. */
public class MpesaShortCode extends BaseEntity {

    @Column(name = "shortcode")
    private String shortcode;

    @Convert(converter = ShortcodeTypeConverter.class)
    @Column(name = "shortcode_type")
    private ShortcodeType shortcodeType;

    @Column(name = "business_name")
    private String businessName;

    @Convert(converter = EncryptedSecretConverter.class)
    @Column(name = "consumer_key")
    private String consumerKey;

    @Convert(converter = EncryptedSecretConverter.class)
    @Column(name = "consumer_secret")
    private String consumerSecret;

    @Convert(converter = EncryptedSecretConverter.class)
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

    /** B2C initiator (API operator) set up on the Daraja portal for this shortcode. */
    @Column(name = "initiator_name")
    private String initiatorName;

    /** Initiator password; encrypted with Safaricom's certificate into the security credential. */
    @Convert(converter = EncryptedSecretConverter.class)
    @Column(name = "initiator_password")
    private String initiatorPassword;

    /** A security credential generated on the Daraja portal; used as is when set. */
    @Convert(converter = EncryptedSecretConverter.class)
    @Column(name = "security_credential")
    private String securityCredential;

    /** Where Safaricom posts B2C results and timeouts; defaults to this service's callback base URL. */
    @Column(name = "b2c_result_url")
    private String b2cResultUrl;

    @Column(name = "b2c_timeout_url")
    private String b2cTimeoutUrl;

    /** First activation; a shortcode never activated is still a draft. */
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "credentials_rotated_at")
    private LocalDateTime credentialsRotatedAt;

    /** Outcome of the last Daraja OAuth token check run from the console. */
    @Column(name = "last_connection_ok")
    private Boolean lastConnectionOk;

    @Column(name = "last_connection_checked_at")
    private LocalDateTime lastConnectionCheckedAt;

    @Column(name = "last_connection_latency_ms")
    private Integer lastConnectionLatencyMs;

    @Column(name = "last_connection_error")
    private String lastConnectionError;
}
