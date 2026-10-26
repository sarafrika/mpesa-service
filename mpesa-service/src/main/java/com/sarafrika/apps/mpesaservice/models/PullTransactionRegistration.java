package com.sarafrika.apps.mpesaservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A shortcode's latest Pull Transactions registration with Safaricom, whatever the outcome. */
@Entity
@Table(name = "pull_transaction_registrations")
@Getter
@Setter
@NoArgsConstructor
public class PullTransactionRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @UuidGenerator
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Column(name = "nominated_number", nullable = false)
    private String nominatedNumber;

    @Column(name = "callback_url", nullable = false)
    private String callbackUrl;

    @Column(name = "registered", nullable = false)
    private boolean registered;

    @Column(name = "response_status")
    private String responseStatus;

    @Column(name = "response_description")
    private String responseDescription;

    @Column(name = "response_ref_id")
    private String responseRefId;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw", columnDefinition = "jsonb")
    private String raw;

    @Column(name = "registered_by")
    private String registeredBy;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "last_nightly_date")
    private LocalDate lastNightlyDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
