package com.sarafrika.apps.mpesaservice.services.metrics;

import com.sarafrika.apps.mpesaservice.utils.DarajaTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Payment timestamps are TIMESTAMPTZ instants; days and hours in reports are Africa/Nairobi. */
public final class MetricsTime {

    public static final ZoneId REPORTING_ZONE = DarajaTime.ZONE;

    private MetricsTime() {
    }

    /** A JDBC parameter for a TIMESTAMPTZ comparison (the driver takes OffsetDateTime, not Instant). */
    public static OffsetDateTime sql(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
