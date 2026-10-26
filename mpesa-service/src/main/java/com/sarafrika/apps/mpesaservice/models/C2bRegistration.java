package com.sarafrika.apps.mpesaservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** One attempt to register a shortcode's C2B URLs with Safaricom, kept whatever the outcome. */
@Entity
@Table(name = "c2b_registrations")
@Getter
@Setter
@NoArgsConstructor
public class C2bRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @UuidGenerator
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    /** Completed or Cancelled: what Safaricom does when the validation URL cannot be reached. */
    @Column(name = "response_type", nullable = false)
    private String responseType;

    @Column(name = "confirmation_url", nullable = false)
    private String confirmationUrl;

    @Column(name = "validation_url", nullable = false)
    private String validationUrl;

    @Column(name = "successful", nullable = false)
    private boolean successful;

    @Column(name = "result_code")
    private String resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    /** Safaricom's reply (or our error) as JSON text. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw", columnDefinition = "jsonb")
    private String raw;

    @Column(name = "registered_by", nullable = false)
    private String registeredBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
