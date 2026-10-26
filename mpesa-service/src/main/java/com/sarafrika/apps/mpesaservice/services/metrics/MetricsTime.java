package com.sarafrika.apps.mpesaservice.services.metrics;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Payment timestamps are TIMESTAMP columns holding the JVM's local wall time (LocalDateTime.now(),
 * Hibernate's creation stamps), so instants go through the JVM zone; reports use Africa/Nairobi. */
public final class MetricsTime {

    public static final ZoneId REPORTING_ZONE = ZoneId.of("Africa/Nairobi");

    private MetricsTime() {
    }

    public static ZoneId storageZone() {
        return ZoneId.systemDefault();
    }

    public static LocalDateTime toStorage(Instant instant) {
        return LocalDateTime.ofInstant(instant, storageZone());
    }

    public static Instant fromStorage(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().atZone(storageZone()).toInstant();
    }
}
