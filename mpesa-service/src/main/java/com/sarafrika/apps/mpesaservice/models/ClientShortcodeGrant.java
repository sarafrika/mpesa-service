package com.sarafrika.apps.mpesaservice.models;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import com.sarafrika.apps.mpesaservice.utils.converters.GrantOperationsConverter;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One product's (Keycloak client's) access to one shortcode. */
@Entity
@Table(name = "client_shortcode_grants")
@SQLDelete(sql = "UPDATE client_shortcode_grants SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class ClientShortcodeGrant extends BaseEntity {

    /** The product's Keycloak client id, as its tokens carry it in azp. */
    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Convert(converter = GrantOperationsConverter.class)
    @Column(name = "operations", nullable = false)
    private Set<GrantOperation> operations = EnumSet.noneOf(GrantOperation.class);

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public boolean allows(GrantOperation operation) {
        return active && operations != null && operations.contains(operation);
    }
}
