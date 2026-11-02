package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.DarajaTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Active shortcodes whose initiator password lapses within 15 days or already has: Safaricom says "Password
 * is valid for 90 days", after which every initiator call fails until a new one is set on the org portal. */
@Component
@Order(16)
public class InitiatorPasswordAttentionSource implements AttentionSource {

    static final String KIND = "INITIATOR_PASSWORD_EXPIRING";
    static final int WARN_DAYS = 15;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(DarajaTime.ZONE);

    private final MpesaShortCodeRepository shortcodes;
    private final Clock clock;

    public InitiatorPasswordAttentionSource(MpesaShortCodeRepository shortcodes, Optional<Clock> clock) {
        this.shortcodes = shortcodes;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        Instant now = clock.instant();
        List<AttentionItemDto> items = new ArrayList<>();
        for (MpesaShortCode code : shortcodes.findByIsActiveTrue()) {
            Instant expires = code.initiatorCredentialExpiresAt();
            if (expires == null || !scope.includes(code.getUuid())
                    || scope.environment() != null && scope.environment() != code.getEnvironment()
                    || expires.isAfter(now.plus(Duration.ofDays(WARN_DAYS)))) {
                continue;
            }
            items.add(item(code, expires, now));
        }
        return items;
    }

    private static AttentionItemDto item(MpesaShortCode code, Instant expires, Instant now) {
        String href = "/paybills/" + code.getUuid() + "?tab=credentials";
        String name = code.getBusinessName() + " · " + code.getShortcode();
        String who = code.getInitiatorName() == null ? "the API initiator" : code.getInitiatorName();
        String renew = " Set a new password for " + who + " on the M-PESA org portal, then save it under Credentials.";
        if (!expires.isAfter(now)) {
            return new AttentionItemDto(KIND, AttentionItemDto.CRITICAL,
                    "Initiator password expired on " + name,
                    "It was saved over 90 days ago, so reversals, status, balance and payouts fail." + renew,
                    href, code.getUuid());
        }
        long days = Math.max(1, ChronoUnit.DAYS.between(now, expires));
        return new AttentionItemDto(KIND, AttentionItemDto.WARNING,
                "Initiator password on " + name + " expires in " + days + (days == 1 ? " day" : " days"),
                "It lapses on " + DAY.format(expires) + ", 90 days after it was saved here." + renew,
                href, code.getUuid());
    }
}
