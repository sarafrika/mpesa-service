package com.sarafrika.apps.mpesaservice.models;

import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shortcode_features")
@Getter
@Setter
@NoArgsConstructor
public class ShortcodeFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 40)
    private DarajaFeature feature;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
