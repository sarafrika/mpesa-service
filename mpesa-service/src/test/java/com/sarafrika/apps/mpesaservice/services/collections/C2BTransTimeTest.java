package com.sarafrika.apps.mpesaservice.services.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class C2BTransTimeTest {

    @Test
    void aConfirmationsTransTimeIsTheInstantItNamesInNairobi() {
        assertThat(C2BConfirmationService.parseTransTime("20260602171907"))
                .isEqualTo(Instant.parse("2026-06-02T14:19:07Z"));
        assertThat(C2BConfirmationService.parseTransTime("  ")).isNull();
        assertThat(C2BConfirmationService.parseTransTime("yesterday")).isNull();
    }
}
