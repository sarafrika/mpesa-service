package com.sarafrika.apps.mpesaservice.services.treasury;

import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceQuerySource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Asks every active shortcode with an initiator for its balance each mpesa.treasury.balance-query-interval.
 * Off unless that is set; safe on several instances because each pass claims its shortcodes first. */
@Slf4j
@Component
public class BalanceQueryScheduler {

    private final TreasuryProperties properties;
    private final BalanceQueryClaims claims;
    private final MpesaShortCodeRepository shortcodes;
    private final BalanceService balances;
    private final Clock clock;

    public BalanceQueryScheduler(TreasuryProperties properties, BalanceQueryClaims claims,
                                 MpesaShortCodeRepository shortcodes, BalanceService balances,
                                 Optional<Clock> clock) {
        this.properties = properties;
        this.claims = claims;
        this.shortcodes = shortcodes;
        this.balances = balances;
        this.clock = clock.orElse(Clock.systemDefaultZone());
    }

    @Scheduled(fixedDelayString = "${mpesa.treasury.balance-query-poll:1m}", initialDelayString = "30s")
    public void poll() {
        try {
            runOnce();
        } catch (RuntimeException ex) {
            log.error("Scheduled balance query pass failed", ex);
        }
    }

    /** One pass; returns how many queries it sent. */
    public int runOnce() {
        if (!properties.scheduledQueriesOn()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<UUID> due = claims.claimDue(now, now.plus(properties.balanceQueryInterval()),
                Math.max(1, properties.balanceQueryBatch()));
        int sent = 0;
        for (UUID uuid : due) {
            try {
                var shortcode = shortcodes.findByUuid(uuid);
                if (shortcode.isPresent()) {
                    balances.send(shortcode.get(), BalanceQuerySource.SCHEDULED);
                    sent++;
                }
            } catch (RuntimeException ex) {
                log.warn("Scheduled balance query for shortcode {} failed: {}", uuid, ex.toString());
            }
        }
        return sent;
    }
}
