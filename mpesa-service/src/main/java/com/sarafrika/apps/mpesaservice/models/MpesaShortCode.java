package com.sarafrika.apps.mpesaservice.models;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeCapabilities;
import com.sarafrika.apps.mpesaservice.utils.converters.EncryptedSecretConverter;
import com.sarafrika.apps.mpesaservice.utils.converters.EnvironmentConverter;
import com.sarafrika.apps.mpesaservice.utils.converters.ShortcodeOperationsConverter;
import com.sarafrika.apps.mpesaservice.utils.converters.ShortcodeTypeConverter;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.time.LocalDateTime;

@Entity
@Table(name = "mpesa_shortcodes")
@SQLDelete(sql = "UPDATE mpesa_shortcodes SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
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

    /** A till's store or Head Office number (the Go-Live shortcode); null for every other type. */
    @Column(name = "store_number")
    private String storeNumber;

    /** When someone last confirmed the store number; null on tills migrated with store number = till number. */
    @Column(name = "store_number_confirmed_at")
    private LocalDateTime storeNumberConfirmedAt;

    /** What this shortcode is set up to do; null on an unsaved entity means everything its type allows. */
    @Convert(converter = ShortcodeOperationsConverter.class)
    @Column(name = "operations")
    private Set<ShortcodeOperation> operations;

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

    /** When the initiator password or security credential was last saved; Safaricom expires it after 90 days. */
    @Column(name = "initiator_credential_set_at")
    private LocalDateTime initiatorCredentialSetAt;

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

    @Column(name = "credentials_updated_at")
    private LocalDateTime credentialsUpdatedAt;

    /** Outcome of the last Daraja OAuth token check run from the console. */
    @Column(name = "last_connection_ok")
    private Boolean lastConnectionOk;

    @Column(name = "last_connection_checked_at")
    private LocalDateTime lastConnectionCheckedAt;

    @Column(name = "last_connection_latency_ms")
    private Integer lastConnectionLatencyMs;

    @Column(name = "last_connection_error")
    private String lastConnectionError;

    /** Utility balance (KES) below which this shortcode shows low float; null uses mpesa.alerts.low-float. */
    @Column(name = "low_float_threshold")
    private BigDecimal lowFloatThreshold;

    /** Secret path segment of this shortcode's callback URLs (/api/v1/callbacks/{token}/...); never returned. */
    @Column(name = "callback_token", updatable = false)
    private String callbackToken;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Safaricom: "Password is valid for 90 days" (Account Balance FAQ). */
    public static final int INITIATOR_CREDENTIAL_DAYS = 90;

    /** When the stored initiator password or security credential lapses; null when neither is stored. */
    public LocalDateTime initiatorCredentialExpiresAt() {
        boolean stored = initiatorPassword != null && !initiatorPassword.isBlank()
                || securityCredential != null && !securityCredential.isBlank();
        return stored && initiatorCredentialSetAt != null ? initiatorCredentialSetAt.plusDays(INITIATOR_CREDENTIAL_DAYS)
                : null;
    }

    public static String newCallbackToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @PrePersist
    void ensureCallbackToken() {
        if (callbackToken == null) {
            callbackToken = newCallbackToken();
        }
        if (operations == null) {
            operations = ShortcodeCapabilities.availableOperations(shortcodeType);
        }
    }
}
