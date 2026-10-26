package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.BalanceQuerySource;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceSnapshotStatus;
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

/** One Account Balance query sent to Safaricom and, once its result arrives, the balances it reported. */
@Entity
@Table(name = "balance_snapshots")
@SQLDelete(sql = "UPDATE balance_snapshots SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BalanceSnapshot extends BaseEntity {

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BalanceSnapshotStatus status = BalanceSnapshotStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private BalanceQuerySource source;

    @Column(name = "originator_conversation_id")
    private String originatorConversationId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    /** The parsed accounts as a JSON array of {name, currency, available, current, reserved, uncleared}. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "accounts", columnDefinition = "jsonb")
    private String accounts;

    @Column(name = "utility_available")
    private BigDecimal utilityAvailable;

    @Column(name = "working_available")
    private BigDecimal workingAvailable;

    @Column(name = "charges_paid_available")
    private BigDecimal chargesPaidAvailable;

    /** Safaricom's result callback exactly as received. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw", columnDefinition = "jsonb")
    private String raw;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}
