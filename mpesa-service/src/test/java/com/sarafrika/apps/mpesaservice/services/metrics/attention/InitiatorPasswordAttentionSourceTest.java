package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** An initiator password saved 90 days ago has lapsed; within 15 days of that it is a warning. */
class InitiatorPasswordAttentionSourceTest {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T09:00:00Z"), NAIROBI);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final MpesaShortCodeRepository repository = mock(MpesaShortCodeRepository.class);
    private final InitiatorPasswordAttentionSource source =
            new InitiatorPasswordAttentionSource(repository, Optional.of(CLOCK));

    @Test
    void expiringIsAWarningExpiredIsCriticalAndFreshOrMissingIsQuiet() {
        MpesaShortCode expiring = shortcode("600101", NOW.minusDays(80), "pw", null);
        MpesaShortCode expired = shortcode("600102", NOW.minusDays(91), null, "CRED==");
        MpesaShortCode fresh = shortcode("600103", NOW.minusDays(10), "pw", null);
        MpesaShortCode none = shortcode("600104", NOW.minusDays(200), null, null);
        when(repository.findByIsActiveTrue()).thenReturn(List.of(expiring, expired, fresh, none));

        List<AttentionItemDto> items = source.currentItems(AttentionScope.ALL);

        assertThat(items).extracting(AttentionItemDto::shortcodeUuid)
                .containsExactly(expiring.getUuid(), expired.getUuid());
        assertThat(items.get(0).kind()).isEqualTo(InitiatorPasswordAttentionSource.KIND);
        assertThat(items.get(0).level()).isEqualTo(AttentionItemDto.WARNING);
        assertThat(items.get(0).title()).contains("expires in 10 days");
        assertThat(items.get(0).href()).endsWith("?tab=credentials");
        assertThat(items.get(1).level()).isEqualTo(AttentionItemDto.CRITICAL);
        assertThat(source.currentItems(new AttentionScope(null, Environment.SANDBOX))).isEmpty();
        assertThat(source.currentItems(new AttentionScope(expired.getUuid(), null))).hasSize(1);
    }

    private static MpesaShortCode shortcode(String number, LocalDateTime setAt, String password, String credential) {
        MpesaShortCode code = new MpesaShortCode();
        code.setUuid(UUID.randomUUID());
        code.setShortcode(number);
        code.setBusinessName("Payouts");
        code.setShortcodeType(ShortcodeType.B2C);
        code.setEnvironment(Environment.PRODUCTION);
        code.setInitiatorName("api");
        code.setInitiatorPassword(password);
        code.setSecurityCredential(credential);
        code.setInitiatorCredentialSetAt(setAt);
        return code;
    }
}
