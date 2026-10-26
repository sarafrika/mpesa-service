package com.sarafrika.apps.mpesaservice.utils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Payment timestamps are stored as local time of this service; the admin API returns them with an offset. */
public final class Times {

    private Times() {
    }

    public static OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    public static LocalDateTime local(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
