package com.sarafrika.apps.mpesaservice.services.metrics;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.FloatSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.LatestTransactionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.MetricsSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.MetricsTimeseriesDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PeriodMetricsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.ShortcodeFloatDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.TimeseriesPointDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.TypeTotalDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.Direction;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.Filter;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.LatestFloat;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.StatusTotal;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Overview figures: period totals with the previous period, bucketed series, float and latest payments. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsService {

    public static final Duration MAX_RANGE = Duration.ofDays(400);
    public static final Duration MAX_HOURLY_RANGE = Duration.ofDays(31);
    public static final int MAX_LATEST = 20;
    static final List<String> INCOMING_TYPES = List.of("STK_PUSH", "C2B");

    private final MetricsQueries queries;

    public MetricsSummaryDto summary(Instant from, Instant to, Filter filter) {
        validateRange(from, to);
        Instant previousFrom = from.minus(Duration.between(from, to));
        List<StatusTotal> totals = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            totals.addAll(queries.statusTotals(direction, previousFrom, from, to, filter));
        }
        return new MetricsSummaryDto(from, to, previousFrom, from,
                period(totals.stream().filter(StatusTotal::current).toList()),
                period(totals.stream().filter(t -> !t.current()).toList()),
                floatSummary(queries.latestFloats(filter)));
    }

    public MetricsTimeseriesDto timeseries(Instant from, Instant to, String bucket, Filter filter) {
        validateRange(from, to);
        String unit = bucket == null ? "day" : bucket.toLowerCase(Locale.ROOT);
        if (!unit.equals("hour") && !unit.equals("day")) {
            throw new InvalidRequestException("bucket", "must be hour or day");
        }
        if (unit.equals("hour") && Duration.between(from, to).compareTo(MAX_HOURLY_RANGE) > 0) {
            throw new InvalidRequestException("bucket", "hourly buckets cover at most 31 days; use day");
        }
        Map<LocalDateTime, MetricsQueries.BucketTotal> in = new HashMap<>();
        Map<LocalDateTime, MetricsQueries.BucketTotal> out = new HashMap<>();
        queries.successBuckets(Direction.IN, unit, from, to, filter).forEach(b -> in.put(b.bucket(), b));
        queries.successBuckets(Direction.OUT, unit, from, to, filter).forEach(b -> out.put(b.bucket(), b));

        List<TimeseriesPointDto> points = new ArrayList<>();
        ZonedDateTime start = truncate(from.atZone(MetricsTime.REPORTING_ZONE), unit);
        while (start.toInstant().isBefore(to)) {
            LocalDateTime key = start.toLocalDateTime();
            MetricsQueries.BucketTotal collected = in.get(key);
            MetricsQueries.BucketTotal paid = out.get(key);
            points.add(new TimeseriesPointDto(start.toInstant(),
                    collected == null ? BigDecimal.ZERO : collected.amount(),
                    collected == null ? 0 : collected.count(),
                    paid == null ? BigDecimal.ZERO : paid.amount(),
                    paid == null ? 0 : paid.count()));
            start = unit.equals("hour") ? start.plusHours(1) : start.plusDays(1);
        }
        return new MetricsTimeseriesDto(from, to, unit, MetricsTime.REPORTING_ZONE.getId(), points);
    }

    public List<LatestTransactionDto> latest(Integer limit, Filter filter) {
        int size = limit == null ? 6 : limit;
        if (size < 1 || size > MAX_LATEST) {
            throw new InvalidRequestException("limit", "must be between 1 and " + MAX_LATEST);
        }
        return queries.latest(size, filter).stream()
                .map(r -> new LatestTransactionDto(r.uuid(), r.direction().name(), r.paymentType(), r.status(),
                        r.amount(), r.reference(), r.accountReference(), r.shortcodeUuid(), r.shortcode(),
                        r.businessName(), r.createdAt()))
                .toList();
    }

    static void validateRange(Instant from, Instant to) {
        if (from == null) {
            throw new InvalidRequestException("from", "is required");
        }
        if (to == null) {
            throw new InvalidRequestException("to", "is required");
        }
        if (!from.isBefore(to)) {
            throw new InvalidRequestException("to", "must be after from");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new InvalidRequestException("from", "the range may cover at most 400 days");
        }
    }

    private static ZonedDateTime truncate(ZonedDateTime time, String unit) {
        return unit.equals("hour") ? time.truncatedTo(ChronoUnit.HOURS) : time.truncatedTo(ChronoUnit.DAYS);
    }

    private static PeriodMetricsDto period(List<StatusTotal> totals) {
        BigDecimal collected = BigDecimal.ZERO;
        long collectedCount = 0;
        BigDecimal payouts = BigDecimal.ZERO;
        long payoutsCount = 0;
        long success = 0;
        long failed = 0;
        long cancelled = 0;
        long pending = 0;
        Map<String, TypeTotalDto> byType = new HashMap<>();
        INCOMING_TYPES.forEach(type -> byType.put(type, new TypeTotalDto(type, BigDecimal.ZERO, 0)));
        for (StatusTotal total : totals) {
            switch (total.status()) {
                case "SUCCESS" -> {
                    success += total.count();
                    if (total.direction() == Direction.IN) {
                        collected = collected.add(total.amount());
                        collectedCount += total.count();
                        byType.merge(total.paymentType(),
                                new TypeTotalDto(total.paymentType(), total.amount(), total.count()),
                                (a, b) -> new TypeTotalDto(a.paymentType(), a.amount().add(b.amount()),
                                        a.count() + b.count()));
                    } else {
                        payouts = payouts.add(total.amount());
                        payoutsCount += total.count();
                    }
                }
                case "FAILED" -> failed += total.count();
                case "CANCELLED" -> cancelled += total.count();
                default -> pending += total.count();
            }
        }
        long settled = success + failed + cancelled;
        Double rate = settled == 0 ? null : (double) success / settled;
        List<TypeTotalDto> types = byType.values().stream()
                .sorted(Comparator.comparing(TypeTotalDto::paymentType)).toList();
        return new PeriodMetricsDto(collected, collectedCount, types, payouts, payoutsCount, settled, success,
                failed, cancelled, pending, rate);
    }

    static FloatSummaryDto floatSummary(List<LatestFloat> floats) {
        if (floats.isEmpty()) {
            return new FloatSummaryDto(null, 0, null);
        }
        BigDecimal total = floats.stream().map(LatestFloat::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LatestFloat lowest = floats.stream().min(Comparator.comparing(LatestFloat::amount)).orElseThrow();
        return new FloatSummaryDto(total, floats.size(), new ShortcodeFloatDto(lowest.shortcodeUuid(),
                lowest.shortcode(), lowest.businessName(), lowest.amount(), lowest.reportedAt()));
    }
}
