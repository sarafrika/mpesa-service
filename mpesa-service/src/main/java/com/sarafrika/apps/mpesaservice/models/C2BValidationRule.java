package com.sarafrika.apps.mpesaservice.models;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import com.sarafrika.apps.mpesaservice.utils.enums.LookupFailurePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How one shortcode answers Safaricom's C2B validation request. */
@Entity
@Table(name = "c2b_validation_rules")
@SQLDelete(sql = "UPDATE c2b_validation_rules SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class C2BValidationRule extends BaseEntity {

    public static final int DEFAULT_LOOKUP_TIMEOUT_MS = 2000;

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    /** A Java regex the whole BillRefNumber must match; null accepts any account. */
    @Column(name = "account_pattern")
    private String accountPattern;

    @Column(name = "min_amount")
    private BigDecimal minAmount;

    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    /** A product endpoint asked to confirm the account exists before we accept. */
    @Column(name = "lookup_url")
    private String lookupUrl;

    @Column(name = "lookup_timeout_ms", nullable = false)
    private Integer lookupTimeoutMs = DEFAULT_LOOKUP_TIMEOUT_MS;

    @Enumerated(EnumType.STRING)
    @Column(name = "on_lookup_failure", nullable = false)
    private LookupFailurePolicy onLookupFailure = LookupFailurePolicy.REJECT;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
