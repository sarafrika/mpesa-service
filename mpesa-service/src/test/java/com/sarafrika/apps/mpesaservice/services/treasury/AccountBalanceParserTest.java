package com.sarafrika.apps.mpesaservice.services.treasury;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountBalanceParserTest {

    // The AccountBalance value of the result sample on developer.safaricom.co.ke/apis/AccountBalance.
    static final String DARAJA_SAMPLE = "Working Account|KES|700000.00|700000.00|0.00|0.00&Float Account|KES|0.00|"
            + "0.00|0.00|0.00&Utility Account|KES|228037.00|228037.00|0.00|0.00&Charges Paid Account|KES|-1540.00|"
            + "-1540.00|0.00|0.00&Organization Settlement Account|KES|0.00|0.00|0.00|0.00";

    @Test
    void readsEveryAccountOfTheDarajaSampleInOrder() {
        List<AccountBalanceParser.Account> accounts = AccountBalanceParser.parse(DARAJA_SAMPLE);

        assertThat(accounts).extracting(AccountBalanceParser.Account::name).containsExactly("Working Account",
                "Float Account", "Utility Account", "Charges Paid Account", "Organization Settlement Account");
        AccountBalanceParser.Account utility = AccountBalanceParser.find(accounts, "utility account").orElseThrow();
        assertThat(utility.currency()).isEqualTo("KES");
        assertThat(utility.available()).isEqualByComparingTo("228037.00");
        assertThat(AccountBalanceParser.find(accounts, AccountBalanceParser.CHARGES_PAID).orElseThrow().available())
                .isEqualByComparingTo("-1540.00");
    }

    // Daraja's format is name|currency|current|available|reserved|uncleared; this B2B sample has funds reserved.
    @Test
    void separatesCurrentAvailableAndReserved() {
        AccountBalanceParser.Account working = AccountBalanceParser.parse(
                "Working Account|KES|346568.83|6186.83|340382.00|0.00").getFirst();

        assertThat(working.current()).isEqualByComparingTo("346568.83");
        assertThat(working.available()).isEqualByComparingTo("6186.83");
        assertThat(working.reserved()).isEqualByComparingTo("340382.00");
        assertThat(working.uncleared()).isEqualByComparingTo("0.00");
    }

    @Test
    void skipsWhatItCannotReadInsteadOfGuessing() {
        assertThat(AccountBalanceParser.parse(null)).isEmpty();
        assertThat(AccountBalanceParser.parse("")).isEmpty();
        List<AccountBalanceParser.Account> accounts = AccountBalanceParser.parse("garbage&Utility Account|KES|x|5.00");

        assertThat(accounts).hasSize(1);
        assertThat(accounts.getFirst().current()).isNull();
        assertThat(accounts.getFirst().available()).isEqualByComparingTo("5.00");
        assertThat(accounts.getFirst().reserved()).isNull();
    }

    @Test
    void theNewerReportWinsAndATieGoesToTheBalanceResult() {
        LocalDateTime noon = LocalDateTime.of(2026, 10, 26, 12, 0);
        BigDecimal snapshot = new BigDecimal("900000");
        BigDecimal payout = new BigDecimal("1000");

        assertThat(KnownFloat.newest(snapshot, noon, payout, noon.minusHours(1), null, null).source())
                .isEqualTo(KnownFloat.BALANCE_QUERY);
        assertThat(KnownFloat.newest(snapshot, noon, payout, noon, null, null).utility()).isEqualByComparingTo("900000");
        assertThat(KnownFloat.newest(snapshot, noon, payout, noon.plusMinutes(1), null, null).source())
                .isEqualTo(KnownFloat.PAYOUT);
        assertThat(KnownFloat.newest(null, null, payout, noon, null, null).utility()).isEqualByComparingTo("1000");
        assertThat(KnownFloat.newest(null, null, null, null, null, null).known()).isFalse();
    }
}
