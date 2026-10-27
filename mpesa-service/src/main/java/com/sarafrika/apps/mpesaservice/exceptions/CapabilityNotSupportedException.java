package com.sarafrika.apps.mpesaservice.exceptions;

import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import lombok.Getter;

/** The shortcode's type cannot do this (a till cannot pay out, a B2C shortcode cannot collect); 409. */
@Getter
public class CapabilityNotSupportedException extends ConflictException {

    public static final String CODE = "CAPABILITY_NOT_SUPPORTED";

    private final ShortcodeCapability capability;

    public CapabilityNotSupportedException(ShortcodeCapability capability, String message) {
        super(message);
        this.capability = capability;
    }
}
