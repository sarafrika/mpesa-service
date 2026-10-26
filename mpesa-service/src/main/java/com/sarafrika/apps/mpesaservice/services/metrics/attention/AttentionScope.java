package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.util.UUID;

/** The Overview's filters; null means all. Sources that cannot narrow by them may ignore them. */
public record AttentionScope(UUID shortcodeUuid, Environment environment) {

    public static final AttentionScope ALL = new AttentionScope(null, null);

    public boolean includes(UUID shortcode) {
        return shortcodeUuid == null || shortcodeUuid.equals(shortcode);
    }
}
