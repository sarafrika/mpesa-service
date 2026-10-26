package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.ShortcodeFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortcodeFeatureRepository extends JpaRepository<ShortcodeFeature, Long> {

    Optional<ShortcodeFeature> findByShortcodeUuidAndFeature(UUID shortcodeUuid, DarajaFeature feature);

    List<ShortcodeFeature> findByShortcodeUuid(UUID shortcodeUuid);

    List<ShortcodeFeature> findByFeatureAndEnabledTrue(DarajaFeature feature);
}
