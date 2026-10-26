package com.sarafrika.apps.mpesaservice.services.metrics;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PaybillHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PaybillState;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackFailureCounts;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.Direction;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.Filter;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.LatestFloat;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.ShortcodeRow;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.ShortcodeWindow;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per shortcode: today's traffic, last activity, latest float and the state they add up to. */
@Service
@Transactional(readOnly = true)
public class PaybillHealthService {

    static final Duration RECENT = Duration.ofHours(1);

    private final MetricsQueries queries;
    private final AlertProperties alerts;
    private final Clock clock;

    private final ObjectProvider<CallbackFailureCounts> callbackFailures;

    public PaybillHealthService(MetricsQueries queries, AlertProperties alerts, ObjectProvider<Clock> clock,
                                ObjectProvider<CallbackFailureCounts> callbackFailures) {
        this.queries = queries;
        this.alerts = alerts;
        this.clock = clock.getIfUnique(Clock::systemDefaultZone);
        this.callbackFailures = callbackFailures;
    }

    public AlertProperties alerts() {
        return alerts;
    }

    public List<PaybillHealthDto> health(Environment environment) {
        Instant now = clock.instant();
        Instant todayStart = LocalDate.ofInstant(now, MetricsTime.REPORTING_ZONE)
                .atStartOfDay(MetricsTime.REPORTING_ZONE).toInstant();
        Instant recentStart = now.minus(RECENT);
        Map<UUID, ShortcodeWindow> in = byShortcode(queries.shortcodeWindows(Direction.IN, todayStart, recentStart));
        Map<UUID, ShortcodeWindow> out = byShortcode(queries.shortcodeWindows(Direction.OUT, todayStart, recentStart));
        CallbackFailureCounts failureCounts = callbackFailures.getIfAvailable();
        Map<UUID, Long> failures = failureCounts == null ? Map.of()
                : failureCounts.since(now.minus(CallbackFailureCounts.WINDOW));
        Map<UUID, LatestFloat> floats = queries.latestFloats(new Filter(null, environment)).stream()
                .collect(Collectors.toMap(LatestFloat::shortcodeUuid, Function.identity()));

        return queries.shortcodes(environment).stream().map(row -> {
            ShortcodeWindow collected = in.get(row.uuid());
            ShortcodeWindow paid = out.get(row.uuid());
            LatestFloat latestFloat = row.active() ? floats.get(row.uuid()) : null;
            return new PaybillHealthDto(row.uuid(), row.shortcode(), row.businessName(), row.shortcodeType(),
                    row.environment(), row.active(), state(row, latestFloat, failures.getOrDefault(row.uuid(), 0L), now),
                    collected == null ? BigDecimal.ZERO : collected.todayAmount(),
                    collected == null ? 0 : collected.todayCount(),
                    paid == null ? BigDecimal.ZERO : paid.todayAmount(),
                    paid == null ? 0 : paid.todayCount(),
                    row.lastActivityAt(),
                    latestFloat == null ? null : latestFloat.amount(),
                    latestFloat == null ? null : latestFloat.reportedAt(),
                    (collected == null ? 0 : collected.failedRecent()) + (paid == null ? 0 : paid.failedRecent()),
                    (collected == null ? 0 : collected.settledRecent()) + (paid == null ? 0 : paid.settledRecent()),
                    threshold(row));
        }).toList();
    }

    // A shortcode that never had traffic counts its quiet time from when it was added.
    PaybillState state(ShortcodeRow row, LatestFloat latestFloat, long callbackFailures, Instant now) {
        if (!row.active()) {
            return row.draft() ? PaybillState.DRAFT : PaybillState.SUSPENDED;
        }
        if (!Environment.PRODUCTION.name().equals(row.environment())) {
            return PaybillState.SANDBOX;
        }
        if (latestFloat != null && latestFloat.amount().compareTo(threshold(row)) < 0) {
            return PaybillState.LOW_FLOAT;
        }
        Instant lastSeen = row.lastActivityAt() != null ? row.lastActivityAt() : row.createdAt();
        if (lastSeen != null && lastSeen.isBefore(now.minus(alerts.staleAfter()))) {
            return PaybillState.STALE;
        }
        if (callbackFailures > 0) {
            return PaybillState.CALLBACKS_FAILING;
        }
        return PaybillState.HEALTHY;
    }

    /** The shortcode's own low-float threshold, else mpesa.alerts.low-float. */
    BigDecimal threshold(ShortcodeRow row) {
        return row.lowFloatThreshold() != null ? row.lowFloatThreshold() : alerts.lowFloat();
    }

    public Instant now() {
        return clock.instant();
    }

    private static Map<UUID, ShortcodeWindow> byShortcode(List<ShortcodeWindow> windows) {
        Map<UUID, ShortcodeWindow> map = new HashMap<>();
        windows.forEach(w -> map.put(w.shortcodeUuid(), w));
        return map;
    }
}
