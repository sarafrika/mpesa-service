package com.sarafrika.apps.mpesaservice.services.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.LatestTransactionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.MetricsSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.MetricsTimeseriesDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PaybillHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PaybillState;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.TimeseriesPointDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.TypeTotalDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.Filter;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionScope;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionService;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.PaybillAttentionSource;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.PendingStkAttentionSource;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.PossibleStkDuplicatesAttentionSource;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.UnlinkedDepositsAttentionSource;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The Overview aggregates over seeded rows on real migrations, including Nairobi day boundaries. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MetricsConfig.class, MetricsQueries.class, MetricsService.class, PaybillHealthService.class,
        AttentionService.class, PaybillAttentionSource.class, PendingStkAttentionSource.class,
        UnlinkedDepositsAttentionSource.class, PossibleStkDuplicatesAttentionSource.class,
        SecretCipher.class, com.sarafrika.apps.mpesaservice.services.callbacks.CallbackFailureCounts.class,
        MetricsIntegrationTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MetricsIntegrationTest {

    // 12:00 in Nairobi on Monday 26 October 2026.
    static final Instant NOW = Instant.parse("2026-10-26T09:00:00Z");
    static final Instant WEEK_FROM = Instant.parse("2026-10-19T21:00:00Z");
    static final Instant WEEK_TO = Instant.parse("2026-10-26T21:00:00Z");
    static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MetricsService metrics;
    @Autowired
    private PaybillHealthService health;
    @Autowired
    private AttentionService attention;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM balance_snapshots");
        jdbc.update("DELETE FROM mpesa_outgoing_payments");
        jdbc.update("DELETE FROM mpesa_incoming_payments");
        jdbc.update("DELETE FROM mpesa_shortcodes");
    }

    @Test
    void anEmptyInstallAnswersZerosAndNoFloat() {
        MetricsSummaryDto summary = metrics.summary(WEEK_FROM, WEEK_TO, Filter.ALL);

        assertThat(summary.current().collectedAmount()).isEqualByComparingTo("0");
        assertThat(summary.current().successRate()).isNull();
        assertThat(summary.current().collectedByType()).extracting(TypeTotalDto::paymentType)
                .containsExactly("B2B_EXPRESS_CHECKOUT", "C2B", "STK_PUSH");
        assertThat(summary.floatSummary().totalAmount()).isNull();
        assertThat(summary.floatSummary().lowest()).isNull();
        assertThat(metrics.timeseries(WEEK_FROM, WEEK_TO, "day", Filter.ALL).points()).hasSize(7)
                .allSatisfy(p -> assertThat(p.collectionsCount()).isZero());
        assertThat(health.health(null)).isEmpty();
        assertThat(attention.items(AttentionScope.ALL)).isEmpty();
        assertThat(metrics.latest(6, Filter.ALL)).isEmpty();
    }

    @Test
    void theSummarySplitsCollectionsByTypeAndComparesWithThePreviousWeek() {
        UUID fees = shortcode("Elimika Fees", "PRODUCTION", true);
        UUID sandbox = shortcode("Daraja sandbox", "SANDBOX", true);
        incoming(fees, "C2B", "SUCCESS", "1000.00", NOW.minus(Duration.ofDays(1)));
        incoming(fees, "STK_PUSH", "SUCCESS", "500.00", NOW.minus(Duration.ofHours(2)));
        incoming(fees, "STK_PUSH", "FAILED", "200.00", NOW.minus(Duration.ofHours(3)));
        incoming(fees, "STK_PUSH", "CANCELLED", "100.00", NOW.minus(Duration.ofHours(4)));
        incoming(fees, "STK_PUSH", "PENDING", "50.00", NOW.minus(Duration.ofHours(5)));
        outgoing(fees, "SUCCESS", "300.00", null, NOW.minus(Duration.ofDays(2)));
        incoming(fees, "C2B", "SUCCESS", "800.00", WEEK_FROM.minus(Duration.ofDays(3)));
        incoming(fees, "C2B", "SUCCESS", "9999.00", WEEK_FROM.minus(Duration.ofDays(30)));
        incoming(sandbox, "C2B", "SUCCESS", "70.00", NOW.minus(Duration.ofHours(1)));
        UUID deleted = incoming(fees, "C2B", "SUCCESS", "5000.00", NOW.minus(Duration.ofHours(1)));
        jdbc.update("UPDATE mpesa_incoming_payments SET deleted_at = NOW() WHERE uuid = ?", deleted);

        MetricsSummaryDto summary = metrics.summary(WEEK_FROM, WEEK_TO, new Filter(null, Environment.PRODUCTION));

        assertThat(summary.previousFrom()).isEqualTo(Instant.parse("2026-10-12T21:00:00Z"));
        assertThat(summary.previousTo()).isEqualTo(WEEK_FROM);
        assertThat(summary.current().collectedAmount()).isEqualByComparingTo("1500.00");
        assertThat(summary.current().collectedCount()).isEqualTo(2);
        assertThat(summary.current().collectedByType())
                .containsExactly(new TypeTotalDto("B2B_EXPRESS_CHECKOUT", BigDecimal.ZERO, 0),
                        new TypeTotalDto("C2B", new BigDecimal("1000.00"), 1),
                        new TypeTotalDto("STK_PUSH", new BigDecimal("500.00"), 1));
        assertThat(summary.current().payoutsAmount()).isEqualByComparingTo("300.00");
        assertThat(summary.current().payoutsCount()).isEqualTo(1);
        assertThat(summary.current().settledCount()).isEqualTo(5);
        assertThat(summary.current().successfulCount()).isEqualTo(3);
        assertThat(summary.current().failedCount()).isEqualTo(1);
        assertThat(summary.current().cancelledCount()).isEqualTo(1);
        assertThat(summary.current().pendingCount()).isEqualTo(1);
        assertThat(summary.current().successRate()).isEqualTo(0.6);
        assertThat(summary.previous().collectedAmount()).isEqualByComparingTo("800.00");
        assertThat(summary.previous().successRate()).isEqualTo(1.0);

        assertThat(metrics.summary(WEEK_FROM, WEEK_TO, new Filter(sandbox, null)).current().collectedAmount())
                .isEqualByComparingTo("70.00");
    }

    @Test
    void b2bExpressCheckoutPaymentsAreCollectionsWithTheirOwnSplit() {
        UUID fees = shortcode("Elimika Fees", "PRODUCTION", true);
        incoming(fees, "B2B_EXPRESS_CHECKOUT", "SUCCESS", "250.00", NOW.minus(Duration.ofHours(1)));
        incoming(fees, "C2B", "SUCCESS", "100.00", NOW.minus(Duration.ofHours(2)));

        MetricsSummaryDto summary = metrics.summary(WEEK_FROM, WEEK_TO, Filter.ALL);

        assertThat(summary.current().collectedAmount()).isEqualByComparingTo("350.00");
        assertThat(summary.current().collectedByType()).first()
                .isEqualTo(new TypeTotalDto("B2B_EXPRESS_CHECKOUT", new BigDecimal("250.00"), 1));
    }

    @Test
    void todaySoFarComparesWithTheSameHoursYesterday() {
        UUID fees = shortcode("Elimika Fees", "PRODUCTION", true);
        Instant todayStart = Instant.parse("2026-10-25T21:00:00Z");
        incoming(fees, "C2B", "SUCCESS", "100.00", NOW.minus(Duration.ofHours(1)));
        incoming(fees, "C2B", "SUCCESS", "40.00", NOW.minus(Duration.ofHours(25)));
        incoming(fees, "C2B", "SUCCESS", "900.00", NOW.minus(Duration.ofHours(23)));

        MetricsSummaryDto today = metrics.summary(todayStart, NOW, todayStart.minus(Duration.ofDays(1)), Filter.ALL);

        assertThat(today.previousTo()).isEqualTo(NOW.minus(Duration.ofDays(1)));
        assertThat(today.current().collectedAmount()).isEqualByComparingTo("100.00");
        assertThat(today.previous().collectedAmount()).isEqualByComparingTo("40.00");
        assertThatThrownBy(() -> metrics.summary(todayStart, NOW, todayStart.minus(Duration.ofHours(2)), Filter.ALL))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("must end by from");
    }

    @Test
    void floatIsEachActiveShortcodesNewestReportSummedWithTheLowestNamed() {
        UUID payouts = shortcode("Elimika Payouts", "PRODUCTION", true);
        UUID refunds = shortcode("Refunds", "PRODUCTION", true);
        UUID retired = shortcode("Retired", "PRODUCTION", false);
        outgoing(payouts, "SUCCESS", "100.00", "90000.00", NOW.minus(Duration.ofDays(2)));
        outgoing(payouts, "SUCCESS", "100.00", "38420.00", NOW.minus(Duration.ofHours(1)));
        outgoing(refunds, "SUCCESS", "100.00", "120000.00", NOW.minus(Duration.ofHours(3)));
        outgoing(retired, "SUCCESS", "100.00", "5.00", NOW.minus(Duration.ofHours(3)));

        MetricsSummaryDto summary = metrics.summary(WEEK_FROM, WEEK_TO, Filter.ALL);

        assertThat(summary.floatSummary().totalAmount()).isEqualByComparingTo("158420.00");
        assertThat(summary.floatSummary().shortcodesReporting()).isEqualTo(2);
        assertThat(summary.floatSummary().lowest().shortcodeUuid()).isEqualTo(payouts);
        assertThat(summary.floatSummary().lowest().amount()).isEqualByComparingTo("38420.00");
        assertThat(summary.floatSummary().lowest().reportedAt()).isEqualTo(NOW.minus(Duration.ofHours(1)));
    }

    @Test
    void bucketsFollowNairobiMidnightAndAreZeroFilled() {
        UUID fees = shortcode("Elimika Fees", "PRODUCTION", true);
        // 01:30 on 26 October in Nairobi is still 25 October in UTC.
        incoming(fees, "C2B", "SUCCESS", "400.00", Instant.parse("2026-10-25T22:30:00Z"));
        incoming(fees, "C2B", "SUCCESS", "600.00", Instant.parse("2026-10-26T08:10:00Z"));
        incoming(fees, "C2B", "FAILED", "999.00", Instant.parse("2026-10-26T08:20:00Z"));
        outgoing(fees, "SUCCESS", "250.00", null, Instant.parse("2026-10-20T06:00:00Z"));

        MetricsTimeseriesDto days = metrics.timeseries(WEEK_FROM, WEEK_TO, "day", Filter.ALL);

        assertThat(days.points()).extracting(TimeseriesPointDto::bucketStart).first()
                .isEqualTo(Instant.parse("2026-10-19T21:00:00Z"));
        TimeseriesPointDto today = days.points().get(6);
        assertThat(today.bucketStart()).isEqualTo(Instant.parse("2026-10-25T21:00:00Z"));
        assertThat(today.collectionsAmount()).isEqualByComparingTo("1000.00");
        assertThat(today.collectionsCount()).isEqualTo(2);
        assertThat(days.points().get(0).payoutsAmount()).isEqualByComparingTo("250.00");
        assertThat(days.points().get(3).collectionsAmount()).isEqualByComparingTo("0");

        MetricsTimeseriesDto hours = metrics.timeseries(Instant.parse("2026-10-25T21:00:00Z"), NOW, "hour",
                Filter.ALL);
        assertThat(hours.points()).hasSize(12);
        assertThat(hours.points().get(1).collectionsAmount()).isEqualByComparingTo("400.00");
        assertThat(hours.points().get(11).collectionsAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void rangesAreValidated() {
        assertThatThrownBy(() -> metrics.summary(WEEK_TO, WEEK_FROM, Filter.ALL))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("after from");
        assertThatThrownBy(() -> metrics.summary(WEEK_TO.minus(Duration.ofDays(401)), WEEK_TO, Filter.ALL))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("400 days");
        assertThatThrownBy(() -> metrics.timeseries(WEEK_TO.minus(Duration.ofDays(40)), WEEK_TO, "hour", Filter.ALL))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("31 days");
        assertThatThrownBy(() -> metrics.timeseries(WEEK_FROM, WEEK_TO, "week", Filter.ALL))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> metrics.latest(21, Filter.ALL)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void paybillHealthGivesEachShortcodeAState() {
        UUID healthy = shortcode("Elimika Fees", "PRODUCTION", true);
        UUID lowFloat = shortcode("Elimika Payouts", "PRODUCTION", true);
        UUID stale = shortcode("Events Collections", "PRODUCTION", true);
        UUID sandbox = shortcode("Daraja sandbox", "SANDBOX", true);
        UUID suspended = shortcode("Old till", "PRODUCTION", false);
        jdbc.update("UPDATE mpesa_shortcodes SET activated_at = created_at WHERE uuid = ?", suspended);
        UUID draft = shortcode("New paybill", "PRODUCTION", false);
        incoming(healthy, "C2B", "SUCCESS", "902.00", NOW.minus(Duration.ofMinutes(1)));
        incoming(healthy, "C2B", "SUCCESS", "98.00", Instant.parse("2026-10-25T21:30:00Z"));
        incoming(healthy, "C2B", "SUCCESS", "5000.00", Instant.parse("2026-10-25T20:30:00Z"));
        outgoing(lowFloat, "SUCCESS", "412.00", "38420.00", NOW.minus(Duration.ofMinutes(3)));
        incoming(stale, "C2B", "SUCCESS", "93.00", NOW.minus(Duration.ofHours(7)));
        incoming(sandbox, "C2B", "SUCCESS", "1.00", NOW.minus(Duration.ofDays(3)));
        UUID failing = shortcode("Elimika Results", "PRODUCTION", true);
        UUID linked = incoming(failing, "C2B", "SUCCESS", "10.00", NOW.minus(Duration.ofMinutes(2)));
        jdbc.update("INSERT INTO callback_inbox (endpoint, kind, status, attempts, received_at, payment_uuid) "
                + "VALUES ('/api/v1/callbacks/c2b/confirmation', 'C2B_CONFIRMATION', 'FAILED', 1, ?, ?)",
                java.sql.Timestamp.from(NOW.minus(Duration.ofHours(1))), linked);

        Map<UUID, PaybillHealthDto> byUuid = health.health(null).stream()
                .collect(Collectors.toMap(PaybillHealthDto::shortcodeUuid, Function.identity()));

        assertThat(byUuid.get(healthy).state()).isEqualTo(PaybillState.HEALTHY);
        assertThat(byUuid.get(healthy).collectedTodayAmount()).isEqualByComparingTo("1000.00");
        assertThat(byUuid.get(healthy).collectedTodayCount()).isEqualTo(2);
        assertThat(byUuid.get(healthy).lastActivityAt()).isEqualTo(NOW.minus(Duration.ofMinutes(1)));
        assertThat(byUuid.get(lowFloat).state()).isEqualTo(PaybillState.LOW_FLOAT);
        assertThat(byUuid.get(lowFloat).paidOutTodayAmount()).isEqualByComparingTo("412.00");
        assertThat(byUuid.get(lowFloat).floatAmount()).isEqualByComparingTo("38420.00");
        assertThat(byUuid.get(stale).state()).isEqualTo(PaybillState.STALE);
        assertThat(byUuid.get(sandbox).state()).isEqualTo(PaybillState.SANDBOX);
        assertThat(byUuid.get(suspended).state()).isEqualTo(PaybillState.SUSPENDED);
        assertThat(byUuid.get(draft).state()).isEqualTo(PaybillState.DRAFT);
        assertThat(byUuid.get(failing).state()).isEqualTo(PaybillState.CALLBACKS_FAILING);
        assertThat(health.health(Environment.SANDBOX)).extracting(PaybillHealthDto::shortcodeUuid)
                .containsExactly(sandbox);
    }

    @Test
    void attentionListsFailingLowFloatQuietAndPendingWorstFirst() {
        UUID failing = shortcode("Events Collections", "PRODUCTION", true);
        UUID lowFloat = shortcode("Elimika Payouts", "PRODUCTION", true);
        UUID stale = shortcode("Quiet till", "PRODUCTION", true);
        UUID sandbox = shortcode("Daraja sandbox", "SANDBOX", true);
        for (int i = 0; i < 4; i++) {
            incoming(failing, "C2B", "FAILED", "10.00", NOW.minus(Duration.ofMinutes(10 + i)));
        }
        incoming(failing, "C2B", "SUCCESS", "10.00", NOW.minus(Duration.ofMinutes(5)));
        incoming(failing, "STK_PUSH", "PENDING", "10.00", NOW.minus(Duration.ofMinutes(12)));
        incoming(failing, "STK_PUSH", "PENDING", "10.00", NOW.minus(Duration.ofMinutes(2)));
        outgoing(lowFloat, "SUCCESS", "10.00", "38420.00", NOW.minus(Duration.ofMinutes(3)));
        incoming(stale, "C2B", "SUCCESS", "10.00", NOW.minus(Duration.ofHours(9)));
        incoming(sandbox, "STK_PUSH", "PENDING", "10.00", NOW.minus(Duration.ofHours(9)));

        List<AttentionItemDto> items = attention.items(new AttentionScope(null, Environment.PRODUCTION));

        assertThat(items).extracting(AttentionItemDto::kind)
                .containsExactly("PAYBILL_FAILING", "LOW_FLOAT", "PAYBILL_STALE", "STK_PENDING");
        assertThat(items.get(0).title()).isEqualTo("4 payments failed on Events Collections · "
                + code(failing) + " in the last hour");
        assertThat(items.get(1).level()).isEqualTo(AttentionItemDto.WARNING);
        assertThat(items.get(1).title()).isEqualTo("Elimika Payouts float below KES 50,000.00");
        assertThat(items.get(1).href()).isEqualTo("/paybills/" + lowFloat + "?tab=float");
        assertThat(items.get(2).title()).endsWith("for 9 hours");
        assertThat(items.get(3).title()).isEqualTo("1 STK request pending over 5 minutes");
        assertThat(items.get(3).href()).isEqualTo("/transactions?type=STK_PUSH&status=PENDING");
        assertThat(attention.items(new AttentionScope(failing, null))).extracting(AttentionItemDto::href)
                .contains("/transactions?type=STK_PUSH&status=PENDING&paybill=" + failing);

        assertThat(attention.items(new AttentionScope(lowFloat, null))).extracting(AttentionItemDto::kind)
                .containsExactly("LOW_FLOAT");
    }

    @Test
    void theNewestReportWinsAndEachShortcodeMayHaveItsOwnThreshold() {
        UUID queried = shortcode("Elimika Payouts", "PRODUCTION", true);
        UUID ownLine = shortcode("Refunds", "PRODUCTION", true);
        UUID payoutNewer = shortcode("Events", "PRODUCTION", true);
        outgoing(queried, "SUCCESS", "10.00", "38420.00", NOW.minus(Duration.ofHours(2)));
        snapshot(queried, "900000.00", NOW.minus(Duration.ofHours(1)));
        outgoing(ownLine, "SUCCESS", "10.00", "120000.00", NOW.minus(Duration.ofHours(1)));
        jdbc.update("UPDATE mpesa_shortcodes SET low_float_threshold = 200000 WHERE uuid = ?", ownLine);
        snapshot(payoutNewer, "900000.00", NOW.minus(Duration.ofHours(3)));
        outgoing(payoutNewer, "SUCCESS", "10.00", "1000.00", NOW.minus(Duration.ofHours(1)));

        Map<UUID, PaybillHealthDto> byUuid = health.health(null).stream()
                .collect(Collectors.toMap(PaybillHealthDto::shortcodeUuid, Function.identity()));

        assertThat(byUuid.get(queried).floatAmount()).isEqualByComparingTo("900000.00");
        assertThat(byUuid.get(queried).state()).isNotEqualTo(PaybillState.LOW_FLOAT);
        assertThat(byUuid.get(queried).lowFloatThreshold()).isEqualByComparingTo("50000");
        assertThat(byUuid.get(ownLine).state()).isEqualTo(PaybillState.LOW_FLOAT);
        assertThat(byUuid.get(ownLine).lowFloatThreshold()).isEqualByComparingTo("200000");
        assertThat(byUuid.get(payoutNewer).floatAmount()).isEqualByComparingTo("1000.00");
        assertThat(attention.items(new AttentionScope(ownLine, null)).get(0).title())
                .isEqualTo("Refunds float below KES 200,000.00");
        assertThat(metrics.summary(WEEK_FROM, WEEK_TO, Filter.ALL).floatSummary().totalAmount())
                .isEqualByComparingTo("1021000.00");
    }

    @Test
    void depositsToAnUnknownShortcodeCountInTotalsAndNeedAttention() {
        UUID fees = shortcode("Elimika Fees", "PRODUCTION", true);
        incoming(fees, "C2B", "SUCCESS", "100.00", NOW.minus(Duration.ofHours(1)));
        incoming(null, "C2B", "SUCCESS", "40.00", NOW.minus(Duration.ofHours(2)));

        assertThat(metrics.summary(WEEK_FROM, WEEK_TO, Filter.ALL).current().collectedAmount())
                .isEqualByComparingTo("140.00");
        assertThat(metrics.summary(WEEK_FROM, WEEK_TO, new Filter(null, Environment.PRODUCTION)).current()
                .collectedAmount()).isEqualByComparingTo("100.00");
        assertThat(attention.items(AttentionScope.ALL)).extracting(AttentionItemDto::kind)
                .containsExactly("UNLINKED_DEPOSITS");
        assertThat(attention.items(new AttentionScope(null, Environment.PRODUCTION))).isEmpty();
        assertThat(metrics.latest(5, Filter.ALL)).hasSize(2);
    }

    @Test
    void aC2bDepositSeveralStkPaymentsMayBeNeedsAttentionOnItsPaybill() {
        UUID fees = shortcode("Elimika Fees", "PRODUCTION", true);
        UUID other = shortcode("Elimika Shop", "PRODUCTION", true);
        UUID flagged = incoming(fees, "C2B", "SUCCESS", "100.00", NOW.minus(Duration.ofHours(1)));
        incoming(fees, "C2B", "SUCCESS", "100.00", NOW.minus(Duration.ofHours(2)));
        UUID old = incoming(fees, "C2B", "SUCCESS", "100.00", NOW.minus(Duration.ofDays(9)));
        jdbc.update("UPDATE mpesa_incoming_payments SET possible_stk_duplicates = 2 WHERE uuid IN (?, ?)", flagged, old);

        List<AttentionItemDto> items = attention.items(new AttentionScope(fees, null)).stream()
                .filter(i -> i.kind().equals("POSSIBLE_STK_DUPLICATES")).toList();
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().title()).isEqualTo("1 C2B deposit may repeat an STK payment");
        assertThat(items.getFirst().href()).isEqualTo("/transactions?type=C2B&paybill=" + fees);
        assertThat(attention.items(new AttentionScope(other, null))).extracting(AttentionItemDto::kind)
                .doesNotContain("POSSIBLE_STK_DUPLICATES");
    }

    @Test
    void aReceiptlessStkPaymentSeveralC2bDepositsMayBeNeedsItsOwnAttentionItem() {
        UUID fees = shortcode("Elimika Fees", "PRODUCTION", true);
        UUID flagged = incoming(fees, "STK_PUSH", "SUCCESS", "100.00", NOW.minus(Duration.ofHours(1)));
        jdbc.update("UPDATE mpesa_incoming_payments SET possible_stk_duplicates = 2 WHERE uuid = ?", flagged);

        List<AttentionItemDto> items = attention.items(new AttentionScope(fees, null));
        assertThat(items).extracting(AttentionItemDto::kind).contains("POSSIBLE_C2B_DUPLICATES")
                .doesNotContain("POSSIBLE_STK_DUPLICATES");
        AttentionItemDto item = items.stream().filter(i -> i.kind().equals("POSSIBLE_C2B_DUPLICATES")).findFirst()
                .orElseThrow();
        assertThat(item.title()).isEqualTo("1 STK payment may repeat a C2B deposit");
        assertThat(item.href()).isEqualTo("/transactions?type=STK_PUSH&paybill=" + fees);
    }

    @Test
    void latestMergesBothTablesNewestFirst() {
        UUID fees = shortcode("Elimika Fees", "PRODUCTION", true);
        incoming(fees, "C2B", "SUCCESS", "4500.00", NOW.minus(Duration.ofSeconds(10)));
        outgoing(fees, "FAILED", "750.00", null, NOW.minus(Duration.ofSeconds(20)));
        incoming(fees, "STK_PUSH", "PENDING", "1250.00", NOW.minus(Duration.ofSeconds(30)));
        incoming(fees, "C2B", "SUCCESS", "1.00", NOW.minus(Duration.ofDays(30)));

        List<LatestTransactionDto> latest = metrics.latest(3, Filter.ALL);

        assertThat(latest).extracting(LatestTransactionDto::direction).containsExactly("IN", "OUT", "IN");
        assertThat(latest.get(0).createdAt()).isEqualTo(NOW.minus(Duration.ofSeconds(10)));
        assertThat(latest.get(0).reference()).startsWith("TX");
        assertThat(latest.get(1).paymentType()).isEqualTo("B2C");
        assertThat(latest.get(2).reference()).startsWith("ws_CO_");
        assertThat(latest.get(0).businessName()).isEqualTo("Elimika Fees");
    }

    private UUID shortcode(String name, String environment, boolean active) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO mpesa_shortcodes (uuid, shortcode, shortcode_type, business_name, consumer_key, "
                        + "consumer_secret, callback_url, environment, is_active, created_at) "
                        + "VALUES (?, ?, 'PAYBILL', ?, 'k', 's', 'https://example.test/cb', ?, ?, ?)",
                uuid, String.valueOf(600000 + SEQUENCE.incrementAndGet()), name, environment, active,
                MetricsTime.toStorage(NOW.minus(Duration.ofDays(60))));
        return uuid;
    }

    private String code(UUID shortcode) {
        return jdbc.queryForObject("SELECT shortcode FROM mpesa_shortcodes WHERE uuid = ?", String.class, shortcode);
    }

    private UUID incoming(UUID shortcode, String type, String status, String amount, Instant at) {
        UUID uuid = UUID.randomUUID();
        int n = SEQUENCE.incrementAndGet();
        boolean settled = !status.equals("PENDING");
        jdbc.update("INSERT INTO mpesa_incoming_payments (uuid, shortcode_uuid, payment_type, transaction_id, "
                        + "checkout_request_id, phone_number, amount, status, created_at, processed_at) "
                        + "VALUES (?, ?, ?, ?, ?, '254700000000', ?, ?, ?, ?)",
                uuid, shortcode, type, settled ? "TX" + n : "ws_CO_" + n, "ws_CO_" + n, new BigDecimal(amount),
                status, MetricsTime.toStorage(at), settled ? MetricsTime.toStorage(at) : null);
        return uuid;
    }

    private void snapshot(UUID shortcode, String utility, Instant at) {
        jdbc.update("INSERT INTO balance_snapshots (shortcode_uuid, status, source, utility_available, requested_at, "
                + "received_at) VALUES (?, 'COMPLETED', 'MANUAL', ?, ?, ?)", shortcode, new BigDecimal(utility),
                MetricsTime.toStorage(at), MetricsTime.toStorage(at));
    }

    private void outgoing(UUID shortcode, String status, String amount, String utilityFunds, Instant at) {
        int n = SEQUENCE.incrementAndGet();
        jdbc.update("INSERT INTO mpesa_outgoing_payments (shortcode_uuid, payment_type, transaction_id, "
                        + "originator_conversation_id, recipient_phone_number, amount, command_id, initiator_name, "
                        + "status, utility_account_available_funds, created_at, processed_at) "
                        + "VALUES (?, 'B2C', ?, ?, '254700000000', ?, 'BusinessPayment', 'api', ?, ?, ?, ?)",
                shortcode, status.equals("SUCCESS") ? "OUT" + n : null, "sar-" + n, new BigDecimal(amount), status,
                utilityFunds == null ? null : new BigDecimal(utilityFunds), MetricsTime.toStorage(at),
                MetricsTime.toStorage(at));
    }
}
