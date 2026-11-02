package com.sarafrika.apps.mpesaservice.services.treasury;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnownFloatTest {

    private static final Instant T = Instant.parse("2026-10-31T09:00:00Z");
    private static final BigDecimal QUERY = new BigDecimal("100.00");
    private static final BigDecimal PAYOUT = new BigDecimal("200.00");
    private static final BigDecimal C2B = new BigDecimal("300.00");

    @Test
    void theNewestOfTheThreeReportsWins() {
        assertThat(KnownFloat.newest(QUERY, T, PAYOUT, T.minus(Duration.ofMinutes(1)), C2B, T.minus(Duration.ofMinutes(2))))
                .isEqualTo(new KnownFloat(QUERY, T, KnownFloat.BALANCE_QUERY));
        assertThat(KnownFloat.newest(QUERY, T.minus(Duration.ofMinutes(2)), PAYOUT, T, C2B, T.minus(Duration.ofMinutes(1))))
                .isEqualTo(new KnownFloat(PAYOUT, T, KnownFloat.PAYOUT));
        assertThat(KnownFloat.newest(QUERY, T.minus(Duration.ofMinutes(2)), PAYOUT, T.minus(Duration.ofMinutes(1)), C2B, T))
                .isEqualTo(new KnownFloat(C2B, T, KnownFloat.C2B_PAYMENT));
    }

    @Test
    void aTieGoesToTheBalanceResultThenThePayout() {
        assertThat(KnownFloat.newest(QUERY, T, PAYOUT, T, C2B, T).source()).isEqualTo(KnownFloat.BALANCE_QUERY);
        assertThat(KnownFloat.newest(null, null, PAYOUT, T, C2B, T).source()).isEqualTo(KnownFloat.PAYOUT);
        assertThat(KnownFloat.newest(QUERY, T, null, null, C2B, T).source()).isEqualTo(KnownFloat.BALANCE_QUERY);
    }

    @Test
    void anyOneReportIsKnownAndNoneIsUnknown() {
        assertThat(KnownFloat.newest(null, null, null, null, C2B, T))
                .isEqualTo(new KnownFloat(C2B, T, KnownFloat.C2B_PAYMENT));
        assertThat(KnownFloat.newest(null, null, null, null, null, null)).isEqualTo(KnownFloat.UNKNOWN);
        assertThat(KnownFloat.newest(null, null, null, null, null, null).known()).isFalse();
        // A report without a time loses to one with a time.
        assertThat(KnownFloat.newest(QUERY, null, null, null, C2B, T).source()).isEqualTo(KnownFloat.C2B_PAYMENT);
    }
}
