package com.sarafrika.apps.mpesaservice.services.treasury;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnownWorkingTest {

    private static final Instant T = Instant.parse("2026-11-04T09:00:00Z");
    private static final BigDecimal QUERY = new BigDecimal("100.00");
    private static final BigDecimal RESULT = new BigDecimal("6186.83");

    @Test
    void theNewerOfABalanceResultAndAB2bResultWinsAndATieGoesToTheBalanceResult() {
        assertThat(KnownWorking.newest(QUERY, T, RESULT, T.plus(Duration.ofMinutes(1))))
                .isEqualTo(new KnownWorking(RESULT, T.plus(Duration.ofMinutes(1)), KnownWorking.B2B_RESULT));
        assertThat(KnownWorking.newest(QUERY, T, RESULT, T.minus(Duration.ofMinutes(1))).source())
                .isEqualTo(KnownWorking.BALANCE_QUERY);
        assertThat(KnownWorking.newest(QUERY, T, RESULT, T).source()).isEqualTo(KnownWorking.BALANCE_QUERY);
    }

    @Test
    void eitherAloneIsKnownAndNeitherIsUnknown() {
        assertThat(KnownWorking.newest(null, T, RESULT, null).available()).isEqualByComparingTo(RESULT);
        assertThat(KnownWorking.newest(QUERY, T, null, null).available()).isEqualByComparingTo(QUERY);
        assertThat(KnownWorking.newest(null, null, null, null).known()).isFalse();
    }

    @Test
    void thePreCheckWarnsWithoutBlocking() {
        assertThat(B2bSweepService.workingCheck(BigDecimal.TEN, KnownWorking.UNKNOWN).text())
                .isEqualTo("No Working balance reading yet");
        assertThat(B2bSweepService.workingCheck(new BigDecimal("7000"), new KnownWorking(RESULT, T,
                KnownWorking.B2B_RESULT)).text()).isEqualTo("Working KES 6,186.83 is below the amount; move funds "
                + "from Utility to Working on the M-PESA org portal first");
        assertThat(B2bSweepService.workingCheck(new BigDecimal("190"), new KnownWorking(RESULT, T,
                KnownWorking.B2B_RESULT)).state()).isEqualTo("OK");
    }
}
