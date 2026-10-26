package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import io.swagger.v3.oas.annotations.media.Schema;

/** DRAFT until first activated; then ACTIVE, or SUSPENDED while machine calls are refused. */
@Schema(enumAsRef = true)
public enum ShortcodeStatus {
    DRAFT, ACTIVE, SUSPENDED
}
