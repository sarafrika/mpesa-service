package com.sarafrika.apps.mpesaservice.services.features;

import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Refuses a flagged product's flow with 409 while its switch is off on the shortcode. */
@Component
@RequiredArgsConstructor
public class FeatureGate {

    private final ShortcodeFeatures features;

    public void require(UUID shortcodeUuid, DarajaFeature feature, String label) {
        if (!features.isEnabled(shortcodeUuid, feature)) {
            throw new ConflictException(label + " is switched off for this shortcode. An admin can switch it on "
                    + "under Features once Safaricom has enabled it on the app.");
        }
    }
}
