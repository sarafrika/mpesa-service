package com.sarafrika.apps.mpesaservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/** One change made through the console; rows are only ever inserted. */
@Entity
@Immutable
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @UuidGenerator
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    /** The record before the change, as redacted JSON text. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "before", columnDefinition = "jsonb")
    private String before;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "after", columnDefinition = "jsonb")
    private String after;

    @Column(name = "ip")
    private String ip;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
