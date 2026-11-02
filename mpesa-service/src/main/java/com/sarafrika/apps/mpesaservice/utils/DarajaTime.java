package com.sarafrika.apps.mpesaservice.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Safaricom's times (TransTime, TransactionDate, statement lines, request Timestamps) are East Africa Time wall
 * clock with no offset; this turns them into instants and back, whatever zone the JVM runs in. */
public final class DarajaTime {

    public static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    /** yyyyMMddHHmmss: C2B TransTime, STK TransactionDate and the STK/QR request Timestamp. */
    public static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private DarajaTime() {
    }

    public static Instant of(LocalDateTime wallTime) {
        return wallTime == null ? null : wallTime.atZone(ZONE).toInstant();
    }

    public static LocalDateTime wall(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZONE);
    }

    /** Null for a blank or unreadable value. */
    public static Instant parse(Object value, DateTimeFormatter format) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return of(LocalDateTime.parse(value.toString().trim(), format));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Instant parseCompact(Object value) {
        return parse(value, COMPACT);
    }

    public static String format(Instant instant, DateTimeFormatter format) {
        return wall(instant).format(format);
    }

    public static String compact(Instant instant) {
        return format(instant, COMPACT);
    }
}
