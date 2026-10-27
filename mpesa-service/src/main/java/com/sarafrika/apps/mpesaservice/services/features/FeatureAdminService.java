package com.sarafrika.apps.mpesaservice.services.features;

import com.sarafrika.apps.mpesaservice.dtos.admin.features.ShortcodeFeatureDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.ShortcodeFeature;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeCapabilities;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.ShortcodeFeatureRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists and switches a shortcode's flagged Daraja products; every switch is audited. */
@Service
@RequiredArgsConstructor
public class FeatureAdminService {

    static final String AUDIT_ACTION = "SHORTCODE_FEATURE_UPDATED";
    static final String RATIBA_UNAVAILABLE = "Not available yet: Safaricom's API details for Ratiba are unconfirmed";

    private final MpesaShortCodeRepository shortcodes;
    private final ShortcodeFeatureRepository repository;
    private final ShortcodeFeatures features;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<ShortcodeFeatureDto> list(UUID shortcodeUuid) {
        MpesaShortCode shortcode = requireShortcode(shortcodeUuid);
        Map<DarajaFeature, ShortcodeFeature> rows = repository.findByShortcodeUuid(shortcodeUuid).stream()
                .collect(Collectors.toMap(ShortcodeFeature::getFeature, Function.identity(), (a, b) -> a));
        return Arrays.stream(DarajaFeature.values())
                .map(feature -> dto(feature, rows.get(feature), ShortcodeCapabilities.allows(shortcode, feature)))
                .toList();
    }

    @Transactional
    public ShortcodeFeatureDto update(UUID shortcodeUuid, DarajaFeature feature, boolean enabled) {
        MpesaShortCode shortcode = requireShortcode(shortcodeUuid);
        // Safaricom's Ratiba pages disagree on fields and codes, so it cannot be switched on yet (off stays allowed).
        if (feature == DarajaFeature.RATIBA && enabled) {
            throw new ConflictException(RATIBA_UNAVAILABLE);
        }
        ShortcodeCapability needed = ShortcodeCapabilities.forFeature(feature);
        if (enabled && needed != null) {
            ShortcodeCapabilities.require(shortcode, needed);
        }
        boolean before = features.isEnabled(shortcodeUuid, feature);
        features.set(shortcodeUuid, feature, enabled, CurrentActor.name());
        ShortcodeFeature row = repository.findByShortcodeUuidAndFeature(shortcodeUuid, feature).orElseThrow();
        audit.record(AUDIT_ACTION, "SHORTCODE", shortcodeUuid.toString(),
                Map.of("feature", feature.name(), "enabled", before),
                Map.of("feature", feature.name(), "enabled", enabled));
        return dto(feature, row, ShortcodeCapabilities.allows(shortcode, feature));
    }

    private MpesaShortCode requireShortcode(UUID shortcodeUuid) {
        return shortcodes.findByUuid(shortcodeUuid).orElseThrow(() -> new NotFoundException("Shortcode not found"));
    }

    private static ShortcodeFeatureDto dto(DarajaFeature feature, ShortcodeFeature row, boolean available) {
        return row == null ? new ShortcodeFeatureDto(feature, false, null, null, available)
                : new ShortcodeFeatureDto(feature, row.isEnabled(), row.getUpdatedBy(),
                Times.offset(row.getUpdatedAt()), available);
    }
}
