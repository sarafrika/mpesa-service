package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.UuidGenerator;

/** One request Safaricom sent to a callback endpoint, stored raw before it was handled. */
@Entity
@Table(name = "callback_inbox")
@Getter
@Setter
@NoArgsConstructor
public class CallbackInboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @UuidGenerator
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 40)
    private CallbackKind kind;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    /** A few request headers (never authorization or cookies) as a JSON object. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "headers", columnDefinition = "jsonb")
    private String headers;

    /** The body exactly as received, unless it was larger than the cap (then null and bodyTruncated). */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "body", columnDefinition = "jsonb")
    private String body;

    @Column(name = "body_size", nullable = false)
    private int bodySize;

    @Column(name = "body_truncated", nullable = false)
    private boolean bodyTruncated;

    /** Receipts and conversation ids found in the body, space separated, for search. */
    @Column(name = "reference", length = 500)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CallbackStatus status = CallbackStatus.RECEIVED;

    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "payment_uuid")
    private UUID paymentUuid;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
