package com.sarafrika.apps.mpesaservice.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class DarajaTimeTest {

    @Test
    void transTimeIsEastAfricaTime() {
        // Payment UF2216NW3X: Safaricom's FinalisedTime 20260602171907, saved at 14:19 UTC.
        assertThat(DarajaTime.parseCompact("20260602171907")).isEqualTo(Instant.parse("2026-06-02T14:19:07Z"));
        assertThat(DarajaTime.parseCompact(20260602171907L)).isEqualTo(Instant.parse("2026-06-02T14:19:07Z"));
        assertThat(DarajaTime.parseCompact(" 20260602171907 ")).isEqualTo(Instant.parse("2026-06-02T14:19:07Z"));
    }

    @Test
    void blankOrUnreadableTimesAreNull() {
        assertThat(DarajaTime.parseCompact(null)).isNull();
        assertThat(DarajaTime.parseCompact("")).isNull();
        assertThat(DarajaTime.parseCompact("2026-06-02")).isNull();
    }

    @Test
    void requestTimestampsAreWrittenInEastAfricaTime() {
        assertThat(DarajaTime.compact(Instant.parse("2026-06-02T22:30:00Z"))).isEqualTo("20260603013000");
        assertThat(DarajaTime.format(Instant.parse("2026-06-02T14:19:07Z"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).isEqualTo("02.06.2026 17:19:07");
        assertThat(DarajaTime.parse("02.06.2026 17:19:07", DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")))
                .isEqualTo(Instant.parse("2026-06-02T14:19:07Z"));
    }
}
