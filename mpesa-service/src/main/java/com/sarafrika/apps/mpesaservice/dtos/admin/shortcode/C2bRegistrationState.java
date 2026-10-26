package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import io.swagger.v3.oas.annotations.media.Schema;

/** The newest C2B URL registration of a shortcode; B2C shortcodes take no C2B payments. */
@Schema(enumAsRef = true)
public enum C2bRegistrationState {
    REGISTERED, FAILED, NOT_REGISTERED, NOT_APPLICABLE
}
