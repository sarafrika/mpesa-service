package com.sarafrika.apps.mpesaservice.services.reconciliation;

import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.features.ShortcodeFeatures;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationSource;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationTrigger;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reconciles yesterday (East Africa Time) through Pull Transactions for each registered shortcode with the
 * feature on. Off unless mpesa.reconciliation.nightly.enabled; each instance claims a shortcode for the day first. */
@Slf4j
@Component
public class NightlyReconciliationScheduler {

    private final ReconciliationProperties properties;
    private final NamedParameterJdbcTemplate jdbc;
    private final ShortcodeFeatures features;
    private final MpesaShortCodeRepository shortcodes;
    private final ReconciliationService service;
    private final Clock clock;

    public NightlyReconciliationScheduler(ReconciliationProperties properties, NamedParameterJdbcTemplate jdbc,
                                          ShortcodeFeatures features, MpesaShortCodeRepository shortcodes,
                                          ReconciliationService service, Optional<Clock> clock) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.features = features;
        this.shortcodes = shortcodes;
        this.service = service;
        this.clock = clock.orElse(Clock.system(ReconciliationService.NAIROBI));
    }

    @Scheduled(cron = "${mpesa.reconciliation.nightly.cron:0 30 2 * * *}", zone = "Africa/Nairobi")
    public void nightly() {
        try {
            runOnce();
        } catch (RuntimeException ex) {
            log.error("Nightly reconciliation pass failed", ex);
        }
    }

    /** One pass; returns how many runs completed. */
    public int runOnce() {
        if (!properties.nightly().enabled()) {
            return 0;
        }
        LocalDate day = LocalDate.now(clock.withZone(ReconciliationService.NAIROBI)).minusDays(1);
        int done = 0;
        for (UUID uuid : claim(day)) {
            var shortcode = shortcodes.findByUuid(uuid);
            if (shortcode.isEmpty() || !features.isEnabled(uuid, DarajaFeature.PULL_TRANSACTIONS)) {
                continue;
            }
            try {
                service.reconcilePull(uuid, day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                        ReconciliationTrigger.SCHEDULED);
                done++;
            } catch (RuntimeException ex) {
                log.warn("Nightly reconciliation of shortcode {} failed: {}", uuid, ex.toString());
                service.recordFailure(shortcode.get(), ReconciliationSource.PULL_TRANSACTIONS, day.atStartOfDay(),
                        day.plusDays(1).atStartOfDay(), ex.getMessage());
            }
        }
        return done;
    }

    // One UPDATE marks the day claimed; SKIP LOCKED keeps a second instance off rows being claimed (Postgres).
    List<UUID> claim(LocalDate day) {
        String sql = """
                UPDATE pull_transaction_registrations SET last_nightly_date = :day, updated_at = CURRENT_TIMESTAMP
                WHERE id IN (
                    SELECT r.id FROM pull_transaction_registrations r
                    JOIN shortcode_features f ON f.shortcode_uuid = r.shortcode_uuid
                         AND f.feature = 'PULL_TRANSACTIONS' AND f.enabled
                    WHERE r.registered AND (r.last_nightly_date IS NULL OR r.last_nightly_date < :day)
                    ORDER BY r.id
                    FOR UPDATE OF r SKIP LOCKED)
                RETURNING shortcode_uuid""";
        return jdbc.queryForList(sql, new MapSqlParameterSource("day", day), UUID.class);
    }
}
