package com.sarafrika.apps.mpesaservice.services.features;

import com.sarafrika.apps.mpesaservice.models.ShortcodeFeature;
import com.sarafrika.apps.mpesaservice.repositories.ShortcodeFeatureRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Which flagged Daraja products a shortcode may use; callers check isEnabled before calling Daraja.
@Service
@RequiredArgsConstructor
public class ShortcodeFeatures {

    private final ShortcodeFeatureRepository repository;

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID shortcodeUuid, DarajaFeature feature) {
        return repository.findByShortcodeUuidAndFeature(shortcodeUuid, feature).map(ShortcodeFeature::isEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<DarajaFeature, Boolean> all(UUID shortcodeUuid) {
        Map<DarajaFeature, Boolean> features = new EnumMap<>(DarajaFeature.class);
        for (DarajaFeature feature : DarajaFeature.values()) {
            features.put(feature, false);
        }
        repository.findByShortcodeUuid(shortcodeUuid).forEach(f -> features.put(f.getFeature(), f.isEnabled()));
        return features;
    }

    /** Switches a feature on or off; the caller checks the role and records the audit event. */
    @Transactional
    public void set(UUID shortcodeUuid, DarajaFeature feature, boolean enabled, String actor) {
        ShortcodeFeature row = repository.findByShortcodeUuidAndFeature(shortcodeUuid, feature).orElseGet(() -> {
            ShortcodeFeature created = new ShortcodeFeature();
            created.setShortcodeUuid(shortcodeUuid);
            created.setFeature(feature);
            return created;
        });
        row.setEnabled(enabled);
        row.setUpdatedBy(actor);
        row.setUpdatedAt(LocalDateTime.now());
        repository.save(row);
    }
}
