package com.sarafrika.apps.mpesaservice.models;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import com.sarafrika.apps.mpesaservice.utils.converters.EncryptedSecretConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Where a product wants its payment events posted, signed with its own secret. */
@Entity
@Table(name = "webhook_subscriptions")
@SQLDelete(sql = "UPDATE webhook_subscriptions SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class WebhookSubscription extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "url", nullable = false)
    private String url;

    /** HMAC signing secret; encrypted at rest and never returned after it is first shown. */
    @Convert(converter = EncryptedSecretConverter.class)
    @Column(name = "secret", nullable = false, columnDefinition = "text")
    private String secret;

    /** Comma-separated event types; empty means every event the product is entitled to. */
    @Column(name = "event_types", nullable = false)
    private String eventTypes = "";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "secret_rotated_at")
    private LocalDateTime secretRotatedAt;

    public Set<String> eventTypeSet() {
        Set<String> types = new LinkedHashSet<>();
        if (eventTypes != null) {
            Arrays.stream(eventTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(types::add);
        }
        return types;
    }

    public void setEventTypeSet(Set<String> types) {
        this.eventTypes = types == null ? "" : String.join(",", types.stream().sorted().toList());
    }

    public boolean wants(String eventType) {
        Set<String> types = eventTypeSet();
        return types.isEmpty() || types.contains(eventType);
    }
}
