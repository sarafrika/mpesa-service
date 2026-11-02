package com.sarafrika.apps.mpesaservice.utils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Timestamps are stored as instants; the admin API returns them as UTC offsets. */
public final class Times {

    private Times() {
    }

    public static OffsetDateTime offset(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
