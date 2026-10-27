package com.sarafrika.apps.mpesaservice.services.collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Sample accounts for the dry run: always matching the pattern, or null when none can be built. */
class AccountSamplesTest {

    @Test
    void plainPatternsGiveAMatchingAccount() {
        assertThat(AccountSamples.sample("^ELM-[0-9]{6}$")).isEqualTo("ELM-000000");
        assertThat(AccountSamples.sample("^(ELM|SAR)\\d{3,5}$")).isEqualTo("ELM000");
        assertThat(AccountSamples.sample("[A-Z]{2}-?\\d+")).isEqualTo("AA-0");
        assertThat(AccountSamples.sample("^INV\\.\\w{4}$")).isEqualTo("INV.AAAA");
    }

    @Test
    void patternsItCannotSatisfyGiveNull() {
        assertThat(AccountSamples.sample("^[^0-9]+$")).isNull();
        assertThat(AccountSamples.sample("^(?=ELM).*$")).isNull();
        assertThat(AccountSamples.sample("^\\s+$")).isNull();
        assertThat(AccountSamples.sample("")).isNull();
    }
}
