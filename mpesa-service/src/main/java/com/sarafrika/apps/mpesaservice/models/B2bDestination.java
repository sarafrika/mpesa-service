package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.B2bReceiverType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** A paybill or till a shortcode may sweep money to with B2B; only active ones can be chosen. */
@Entity
@Table(name = "b2b_destinations")
@SQLDelete(sql = "UPDATE b2b_destinations SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class B2bDestination extends BaseEntity {

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "receiver_shortcode", nullable = false)
    private String receiverShortcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "receiver_type", nullable = false)
    private B2bReceiverType receiverType;

    /** The account number at the receiving paybill; tills take none. */
    @Column(name = "account_reference")
    private String accountReference;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
