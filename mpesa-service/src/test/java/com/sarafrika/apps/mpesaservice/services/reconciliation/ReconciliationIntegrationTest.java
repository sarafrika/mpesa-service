package com.sarafrika.apps.mpesaservice.services.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.PullTransactionsStatusDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationExceptionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationRunDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.StatusQueryDto;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.WebhookSubscription;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookSubscriptionRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.collections.C2BConfirmationService;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionStatusService;
import com.sarafrika.apps.mpesaservice.services.features.ShortcodeFeatures;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsService;
import com.sarafrika.apps.mpesaservice.services.webhooks.PaymentEventWebhookListener;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookDispatcher;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookFanOutService;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionKind;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationTrigger;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs on the real migrations (Hibernate validate): statement runs, backfill, merge, Pull Transactions gating
 * and the nightly claim. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReconciliationService.class, ReconciliationResolver.class, ReconciliationLedger.class,
        PullTransactionsService.class, NightlyReconciliationScheduler.class, ReconciliationConfig.class, StkCandidates.class,
        ShortcodeFeatures.class, C2BConfirmationService.class, AuditService.class, SecretCipher.class,
        OpenExceptionsAttentionSource.class, WebhookFanOutService.class, PaymentEventWebhookListener.class,
        MetricsService.class, MetricsQueries.class, ReconciliationIntegrationTest.Beans.class})
@TestPropertySource(properties = {"mpesa.reconciliation.nightly.enabled=true",
        "mpesa.callback.base-url=https://example.test/api/v1/callbacks"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReconciliationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class Beans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of());
        }

        @Bean
        Events events() {
            return new Events();
        }
    }

    static class Events {
        final Queue<PaymentEvent> all = new ConcurrentLinkedQueue<>();

        @EventListener
        void on(PaymentEvent event) {
            all.add(event);
        }
    }

    @Autowired
    private ReconciliationService service;
    @Autowired
    private ReconciliationResolver resolver;
    @Autowired
    private PullTransactionsService pull;
    @Autowired
    private NightlyReconciliationScheduler nightly;
    @Autowired
    private OpenExceptionsAttentionSource attention;
    @Autowired
    private ShortcodeFeatures features;
    @Autowired
    private MpesaShortCodeRepository shortcodes;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Events events;
    @MockitoBean
    private MpesaDarajaService daraja;
    @MockitoBean
    private TransactionStatusService statusService;
    @MockitoBean
    private WebhookDispatcher dispatcher;
    @Autowired
    private WebhookSubscriptionRepository subscriptions;
    @Autowired
    private MetricsService metrics;

    private MpesaShortCode shortcode;
    /** Receipts are unique across the shared database, so each test uses its own 7-character prefix. */
    private String p;

    @BeforeEach
    void setUp() {
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(String.valueOf(600000 + (int) (Math.random() * 399999)));
        code.setShortcodeType(ShortcodeType.PAYBILL);
        code.setBusinessName("Elimika Fees");
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setCallbackUrl("https://example.test/api/v1/callbacks/stk/callback");
        code.setEnvironment(Environment.SANDBOX);
        code.setInitiatorName("elimika-api");
        shortcode = shortcodes.saveAndFlush(code);
        events.all.clear();
        p = UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
    }

    @Test
    void aStatementRunStoresCountsExceptionsAndASummary() throws Exception {
        deposit(p + "1AA", "5000.00", LocalDateTime.of(2026, 10, 25, 9, 15, 2));
        payout(p + "2CC", "3000.00", LocalDateTime.of(2026, 10, 25, 11, 30));
        deposit(p + "ZZZ", "1200.00", LocalDateTime.of(2026, 10, 25, 16, 0));

        ReconciliationRunDto run = upload();

        assertThat(run.matchedCount()).isEqualTo(2);
        assertThat(run.exceptionCount()).isEqualTo(3);
        assertThat(run.openExceptions()).isEqualTo(3);
        assertThat(run.matchRate()).isEqualByComparingTo("40.00");
        assertThat(run.chargesTotal()).isEqualByComparingTo("15.00");
        ReconciliationSummaryDto summary = service.summary(run.uuid());
        assertThat(summary.kinds()).hasSize(4);
        assertThat(summary.kinds()).filteredOn(k -> k.kind().equals("MISSING_IN_LEDGER"))
                .singleElement().satisfies(k -> assertThat(k.open()).isEqualTo(2));

        var page = service.listExceptions(run.uuid(), ReconciliationExceptionKind.MISSING_IN_LEDGER,
                ReconciliationExceptionStatus.OPEN, PageRequest.of(0, 25));
        assertThat(page.getTotalElements()).isEqualTo(2);
        ReconciliationExceptionDto reversed = find(page.getContent(), p + "4EE");
        assertThat(reversed.allowedActions()).containsExactly("MARK_RESOLVED");
        assertThat(reversed.otherParty()).doesNotContain("Tom"); // masked for a caller without finance
        assertThat(attention.currentItems(com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionScope.ALL))
                .anySatisfy(item -> assertThat(item.shortcodeUuid()).isEqualTo(shortcode.getUuid()));

        // A second run over the same day supersedes the first run's open exceptions.
        ReconciliationRunDto again = upload();
        assertThat(service.getRun(run.uuid()).openExceptions()).isZero();
        assertThat(again.openExceptions()).isEqualTo(3);
    }

    @Test
    void backfillAddsTheDepositOnceMarksItsSourceAndAnnouncesIt() throws Exception {
        ReconciliationRunDto run = upload();
        ReconciliationExceptionDto missing = find(service.listExceptions(run.uuid(), null, null,
                PageRequest.of(0, 25)).getContent(), p + "0BB");
        assertThat(missing.allowedActions()).containsExactly("BACKFILL", "MARK_RESOLVED");

        ReconciliationExceptionDto done = resolver.backfill(missing.uuid());

        assertThat(done.status()).isEqualTo("RESOLVED");
        assertThat(done.ledgerPaymentUuid()).isNotNull();
        Map<String, Object> row = jdbc.queryForMap("SELECT payment_type, amount, source, account_reference, "
                + "shortcode_uuid FROM mpesa_incoming_payments WHERE transaction_id = ?", p + "0BB");
        assertThat(row.get("payment_type")).isEqualTo("C2B");
        assertThat(row.get("source")).isEqualTo("STATEMENT");
        assertThat(row.get("account_reference")).isEqualTo("ELM-003019");
        assertThat(row.get("shortcode_uuid")).isEqualTo(shortcode.getUuid());
        assertThat(events.all).filteredOn(e -> e.type().equals(PaymentEvent.C2B_CONFIRMED)).hasSize(1);
        assertThat(events.all).filteredOn(e -> e.type().equals(PaymentEvent.C2B_CONFIRMED)).singleElement()
                .satisfies(e -> assertThat(e.data()).containsEntry("source", "STATEMENT"));

        assertThatThrownBy(() -> resolver.backfill(missing.uuid())).isInstanceOf(ConflictException.class);
        // The next run's exception for the same receipt is gone: the deposit now matches.
        ReconciliationRunDto next = upload();
        assertThat(service.listExceptions(next.uuid(), null, null, PageRequest.of(0, 25)).getContent())
                .extracting(ReconciliationExceptionDto::receipt).doesNotContain(p + "0BB");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mpesa_incoming_payments WHERE transaction_id = ?",
                Long.class, p + "0BB")).isEqualTo(1);
    }

    @Test
    void aBackfilledDepositReachesEachC2bFeedProductOnceAndIsCountedOnce() throws Exception {
        java.time.Instant before = java.time.Instant.now().minusSeconds(60);
        grant("elimika-backend", "STK_PUSH,C2B_FEED");
        grant("ledger-app", "C2B_FEED");
        grant("shop-api", "STK_PUSH");
        ReconciliationRunDto run = upload();
        ReconciliationExceptionDto missing = find(service.listExceptions(run.uuid(), null, null,
                PageRequest.of(0, 25)).getContent(), p + "0BB");

        resolver.backfill(missing.uuid());
        upload(); // a later run over the same day finds it matched and announces nothing more

        List<Map<String, Object>> sent = jdbc.queryForList("SELECT client_id, payload -> 'data' ->> 'source' AS source "
                + "FROM webhook_deliveries WHERE event_type = 'c2b.confirmed' AND reference = ?", p + "0BB");
        assertThat(sent).extracting(r -> r.get("client_id")).containsExactlyInAnyOrder("elimika-backend", "ledger-app");
        assertThat(sent).extracting(r -> r.get("source")).containsOnly("STATEMENT");
        // Counted on the day the money arrived (the statement's time), not on the day it was backfilled.
        java.time.Instant day = LocalDate.of(2026, 10, 25).atStartOfDay(ReconciliationService.NAIROBI).toInstant();
        var filter = new MetricsQueries.Filter(shortcode.getUuid(), null);
        var period = metrics.summary(day, day.plus(java.time.Duration.ofDays(1)), filter).current();
        assertThat(period.collectedCount()).isEqualTo(1);
        assertThat(period.collectedAmount()).isEqualByComparingTo("2500.00");
        assertThat(metrics.summary(before, java.time.Instant.now().plusSeconds(60), filter).current()
                .collectedCount()).isZero();
    }

    @Test
    void pochiAndTaxPaymentsAreLedgerRowsThatMatchAndAreNeverQueried() throws Exception {
        productPayout("BUSINESS_TO_POCHI", p + "2CC", "3000.00", LocalDateTime.of(2026, 10, 25, 11, 30));
        productPayout("TAX_REMITTANCE", p + "TX9", "500.00", LocalDateTime.of(2026, 10, 25, 15, 0));

        ReconciliationRunDto run = upload();

        List<ReconciliationExceptionDto> all = service.listExceptions(run.uuid(), null, null, PageRequest.of(0, 25))
                .getContent();
        assertThat(all).extracting(ReconciliationExceptionDto::receipt).doesNotContain(p + "2CC");
        ReconciliationExceptionDto tax = find(all, p + "TX9");
        assertThat(tax.kind()).isEqualTo("MISSING_IN_STATEMENT");
        assertThat(tax.ledgerType()).isEqualTo("TAX_REMITTANCE");
        assertThat(tax.allowedActions()).containsExactly("MARK_RESOLVED");
    }

    @Test
    void backfillCreditsTheStatementAmountShownOverAllTheReceiptsLines() {
        LocalDate day = LocalDate.of(2026, 10, 25);
        ReconciliationRunDto run = upload(day, line(p + "M01", day.atTime(9, 0), "Pay Bill Online", "2,500.00", ""),
                line(p + "M01", day.atTime(9, 0), "Adjustment", "", "-100.00"));
        ReconciliationExceptionDto missing = find(service.listExceptions(run.uuid(), null, null,
                PageRequest.of(0, 25)).getContent(), p + "M01");
        assertThat(missing.statementAmount()).isEqualByComparingTo("2400.00");

        resolver.backfill(missing.uuid());

        assertThat(jdbc.queryForObject("SELECT amount FROM mpesa_incoming_payments WHERE transaction_id = ?",
                java.math.BigDecimal.class, p + "M01")).isEqualByComparingTo("2400.00");
    }

    @Test
    void aPullRunRechecksOnlyEarlierPullRunsComparesOnlyC2bRowsAndEndsNow() {
        features.set(shortcode.getUuid(), DarajaFeature.PULL_TRANSACTIONS, true, "admin");
        jdbc.update("INSERT INTO pull_transaction_registrations (shortcode_uuid, nominated_number, callback_url, "
                + "registered) VALUES (?, '0722000000', 'https://example.test/cb', true)", shortcode.getUuid());
        when(daraja.queryPullTransactions(any(), any(), any(), anyInt())).thenReturn(MpesaApiResponse.success(Map.of(
                "ResponseCode", "1001", "Response", List.of(List.of())), 200));
        LocalDate yesterday = LocalDate.now(ReconciliationService.NAIROBI).minusDays(1);
        payout(p + "OUT", "300.00", yesterday.atTime(11, 0));
        ReconciliationRunDto statement = upload(yesterday,
                line(p + "S01", yesterday.atTime(9, 0), "Pay Bill Online", "100.00", ""));
        assertThat(statement.openExceptions()).isEqualTo(2);
        jdbc.update("INSERT INTO mpesa_incoming_payments (shortcode_uuid, payment_type, transaction_id, "
                + "checkout_request_id, phone_number, amount, status, transaction_date, created_at) VALUES (?, "
                + "'STK_PUSH', ?, ?, '254712345678', 50, 'SUCCESS', ?, ?)", shortcode.getUuid(), p + "STK",
                "ws_CO_" + p, yesterday.atTime(10, 0), yesterday.atTime(10, 0));
        deposit(p + "C2B", "75.00", yesterday.atTime(12, 0));
        LocalDateTime start = yesterday.atStartOfDay();

        ReconciliationRunDto first = service.reconcilePull(shortcode.getUuid(), start,
                LocalDateTime.now(ReconciliationService.NAIROBI).plusHours(2), ReconciliationTrigger.MANUAL);

        assertThat(first.periodEnd()).isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(service.listExceptions(first.uuid(), null, null, PageRequest.of(0, 25)).getContent())
                .extracting(ReconciliationExceptionDto::receipt).containsExactly(p + "C2B");
        assertThat(service.getRun(statement.uuid()).openExceptions()).isEqualTo(2);

        ReconciliationRunDto second = service.reconcilePull(shortcode.getUuid(), start,
                LocalDateTime.now(ReconciliationService.NAIROBI), ReconciliationTrigger.MANUAL);
        assertThat(service.getRun(first.uuid()).openExceptions()).isZero();
        assertThat(second.openExceptions()).isEqualTo(1);
        assertThat(service.getRun(statement.uuid()).openExceptions()).isEqualTo(2);
    }

    @Test
    void backfillOfAReceiptThatArrivedMeanwhileAddsNothing() throws Exception {
        ReconciliationRunDto run = upload();
        ReconciliationExceptionDto missing = find(service.listExceptions(run.uuid(), null, null,
                PageRequest.of(0, 25)).getContent(), p + "0BB");
        deposit(p + "0BB", "2500.00", LocalDateTime.of(2026, 10, 25, 10, 1, 44));

        ReconciliationExceptionDto done = resolver.backfill(missing.uuid());

        assertThat(done.resolutionNote()).contains("Already in the ledger");
        assertThat(events.all).isEmpty();
        assertThat(jdbc.queryForObject("SELECT source FROM mpesa_incoming_payments WHERE transaction_id = ?",
                String.class, p + "0BB")).isNull();
    }

    @Test
    void mergeKeepsTheFirstRowAndRefusesRowsThatDiffer() throws Exception {
        deposit(p + "1AA", "5000.00", LocalDateTime.of(2026, 10, 25, 9, 15, 2));
        UUID second = deposit(p.toLowerCase() + "1aa", "5000.00", LocalDateTime.of(2026, 10, 25, 9, 15, 2));
        deposit(p + "0BB", "2500.00", LocalDateTime.of(2026, 10, 25, 10, 1, 44));
        deposit(p.toLowerCase() + "0bb ", "2600.00", LocalDateTime.of(2026, 10, 25, 10, 1, 44));
        ReconciliationRunDto run = upload();
        List<ReconciliationExceptionDto> duplicates = service.listExceptions(run.uuid(),
                ReconciliationExceptionKind.DUPLICATE_IN_LEDGER, null, PageRequest.of(0, 25)).getContent();
        assertThat(duplicates).hasSize(2);

        assertThatThrownBy(() -> resolver.mergeDuplicate(find(duplicates, p + "0BB").uuid()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("differ");
        ReconciliationExceptionDto merged = resolver.mergeDuplicate(find(duplicates, p + "1AA").uuid());

        assertThat(merged.status()).isEqualTo("RESOLVED");
        assertThat(merged.ledgerPaymentUuid()).isNotEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM mpesa_incoming_payments WHERE uuid = ?",
                Boolean.class, second)).isTrue();
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM mpesa_incoming_payments WHERE deleted_at IS NULL "
                + "AND upper(transaction_id) = ?", java.math.BigDecimal.class, p + "1AA")).isEqualByComparingTo("5000");
        assertThat(events.all).isEmpty();
    }

    @Test
    void queryStatusAndMarkResolvedAreRecorded() throws Exception {
        deposit(p + "ZZZ", "1200.00", LocalDateTime.of(2026, 10, 25, 16, 0));
        ReconciliationRunDto run = upload();
        ReconciliationExceptionDto notOnStatement = find(service.listExceptions(run.uuid(),
                ReconciliationExceptionKind.MISSING_IN_STATEMENT, null, PageRequest.of(0, 25)).getContent(), p + "ZZZ");
        UUID query = UUID.randomUUID();
        when(statusService.request(notOnStatement.ledgerPaymentUuid())).thenReturn(new StatusQueryDto(query,
                "TRANSACTION_STATUS", "PENDING", null, null, null, null, "system", OffsetDateTime.now(), null));

        ReconciliationExceptionDto queried = resolver.queryStatus(notOnStatement.uuid());
        assertThat(queried.status()).isEqualTo("OPEN");
        assertThat(queried.statusQueryUuid()).isEqualTo(query);

        ReconciliationExceptionDto resolved = resolver.markResolved(notOnStatement.uuid(), "Settled on the 26th");
        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.allowedActions()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ?", Long.class,
                notOnStatement.uuid().toString())).isEqualTo(2);
    }

    @Test
    void aStatementForAnotherShortcodeIsRefused() {
        assertThatThrownBy(() -> service.reconcileStatement(shortcode.getUuid(), LocalDate.of(2026, 10, 25),
                LocalDate.of(2026, 10, 25), "s.csv", StatementCsvParserTest.fixture("org-statement-utility.csv")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("600100");
    }

    @Test
    void pullTransactionsNeedsTheFeatureAndARegistration() {
        LocalDateTime start = LocalDateTime.now(ReconciliationService.NAIROBI).minusHours(20);
        assertThatThrownBy(() -> service.reconcilePull(shortcode.getUuid(), start, start.plusHours(12),
                ReconciliationTrigger.MANUAL)).isInstanceOf(ConflictException.class).hasMessageContaining("Features");
        assertThatThrownBy(() -> pull.register(shortcode.getUuid(), "0722000000"))
                .isInstanceOf(ConflictException.class);
        verify(daraja, never()).registerPullTransactions(any(), anyString(), anyString());

        features.set(shortcode.getUuid(), DarajaFeature.PULL_TRANSACTIONS, true, "admin");
        assertThatThrownBy(() -> service.reconcilePull(shortcode.getUuid(), start, start.plusHours(12),
                ReconciliationTrigger.MANUAL)).isInstanceOf(ConflictException.class).hasMessageContaining("Register");

        when(daraja.registerPullTransactions(any(), eq("0722000000"), anyString())).thenReturn(MpesaApiResponse.success(
                Map.of("ResponseRefID", "ref-1", "Response Status", "1000",
                        "Response Description", "Shortcode Registered Successfully"), 200));
        PullTransactionsStatusDto status = pull.register(shortcode.getUuid(), "+254 722 000 000");
        assertThat(status.registered()).isTrue();
        assertThat(status.nominatedNumber()).isEqualTo("0722•••••000");
        verify(daraja).registerPullTransactions(any(), eq("0722000000"),
                eq("https://example.test/api/v1/callbacks/pull-transactions/callback"));

        deposit(p + "PL1", "168.00", start.plusHours(1));
        when(daraja.queryPullTransactions(any(), any(), any(), eq(0))).thenReturn(MpesaApiResponse.success(Map.of(
                "ResponseRefID", "26178-42530161-2", "ResponseCode", "1000", "ResponseMessage", "Success",
                "Response", List.of(List.of(
                        Map.of("transactionId", p + "PL1", "trxDate", start.plusHours(1) + "Z", "msisdn", 722000000,
                                "transactiontype", "c2b-pay-bill-debit", "billreference", "INV-1", "amount", "168.00"),
                        Map.of("transactionId", p + "PL2", "trxDate", start.plusHours(2) + "Z", "msisdn", 722000001,
                                "transactiontype", "c2b-pay-bill-debit", "billreference", "INV-2", "amount", "50.00")))),
                200));
        when(daraja.queryPullTransactions(any(), any(), any(), eq(2))).thenReturn(MpesaApiResponse.success(Map.of(
                "ResponseCode", "1001", "ResponseMessage", "No transactions", "Response", List.of(List.of())), 200));

        ReconciliationRunDto run = service.reconcilePull(shortcode.getUuid(), start, start.plusHours(12),
                ReconciliationTrigger.MANUAL);
        assertThat(run.source()).isEqualTo("PULL_TRANSACTIONS");
        assertThat(run.matchedCount()).isEqualTo(1);
        ReconciliationExceptionDto missing = service.listExceptions(run.uuid(), null, null, PageRequest.of(0, 5))
                .getContent().getFirst();
        assertThat(missing.receipt()).isEqualTo(p + "PL2");
        resolver.backfill(missing.uuid());
        assertThat(jdbc.queryForMap("SELECT source, phone_number FROM mpesa_incoming_payments WHERE transaction_id = ?",
                p + "PL2")).containsEntry("source", "PULL_TRANSACTIONS").containsEntry("phone_number", "254722000001");
        // Products can tell a Pull backfill from a live confirmation by the event's source.
        assertThat(events.all).filteredOn(e -> e.type().equals(PaymentEvent.C2B_CONFIRMED)
                        && (p + "PL2").equals(e.data().get("trans_id")))
                .singleElement().satisfies(e -> assertThat(e.data()).containsEntry("source", "PULL_TRANSACTIONS"));

        assertThatThrownBy(() -> service.reconcilePull(shortcode.getUuid(), start.minusDays(3), start,
                ReconciliationTrigger.MANUAL)).isInstanceOf(InvalidRequestException.class).hasMessageContaining("48 hours");
    }

    @Test
    void theNightlyRunClaimsEachShortcodeOncePerDay() {
        features.set(shortcode.getUuid(), DarajaFeature.PULL_TRANSACTIONS, true, "admin");
        jdbc.update("INSERT INTO pull_transaction_registrations (shortcode_uuid, nominated_number, callback_url, "
                + "registered) VALUES (?, '254722000000', 'https://example.test/cb', true)", shortcode.getUuid());
        when(daraja.queryPullTransactions(any(), any(), any(), anyInt())).thenReturn(MpesaApiResponse.success(Map.of(
                "ResponseCode", "1001", "Response", List.of(List.of())), 200));

        LocalDate yesterday = LocalDate.now(ReconciliationService.NAIROBI).minusDays(1);
        assertThat(nightly.claim(yesterday)).contains(shortcode.getUuid());
        assertThat(nightly.claim(yesterday)).doesNotContain(shortcode.getUuid());

        jdbc.update("UPDATE pull_transaction_registrations SET last_nightly_date = NULL WHERE shortcode_uuid = ?",
                shortcode.getUuid());
        nightly.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_runs WHERE shortcode_uuid = ? "
                + "AND trigger_type = 'SCHEDULED' AND status = 'COMPLETED'", Long.class, shortcode.getUuid())).isEqualTo(1);
        nightly.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_runs WHERE shortcode_uuid = ?",
                Long.class, shortcode.getUuid())).isEqualTo(1);
    }

    private ReconciliationRunDto upload() throws Exception {
        byte[] content = new String(StatementCsvParserTest.fixture("org-statement-utility.csv"),
                java.nio.charset.StandardCharsets.UTF_8).replace("Short Code:,600100", "Short Code:,"
                + shortcode.getShortcode()).replace("TJP9X2K", p).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return service.reconcileStatement(shortcode.getUuid(), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25),
                "statement.csv", content);
    }

    private ReconciliationRunDto upload(LocalDate day, String... lines) {
        String csv = "Short Code:," + shortcode.getShortcode() + "\n"
                + "Receipt No.,Completion Time,Initiation Time,Details,Transaction Status,Paid In,Withdrawn,Balance,"
                + "Balance Confirmed,Reason Type,Other Party Info,Linked Transaction ID,A/C No.\n"
                + String.join("\n", lines) + "\n";
        return service.reconcileStatement(shortcode.getUuid(), day, day, "statement.csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String line(String receipt, LocalDateTime at, String details, String paidIn, String withdrawn) {
        String time = at.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        return String.join(",", receipt, time, time, details, "Completed", "\"" + paidIn + "\"",
                "\"" + withdrawn + "\"", "", "true", details, "254722***111 - John ***", "", "ELM-1");
    }

    private void productPayout(String product, String receipt, String amount, LocalDateTime at) {
        jdbc.update("INSERT INTO product_payouts (shortcode_uuid, product, approval_request_uuid, command_id, party_b, "
                + "account_reference, amount, remarks, status, transaction_id, completed_at, created_at) VALUES (?, ?, ?, "
                + "?, ?, ?, ?::numeric, 'test', 'SUCCESS', ?, ?, ?)", shortcode.getUuid(), product, UUID.randomUUID(),
                product.equals("TAX_REMITTANCE") ? "PayTaxToKRA" : "BusinessPayToPochi",
                product.equals("TAX_REMITTANCE") ? "572572" : "254705912645",
                product.equals("TAX_REMITTANCE") ? "PRN1" : null, amount, receipt, at, at);
    }

    private void grant(String clientId, String operations) {
        jdbc.update("INSERT INTO client_shortcode_grants (client_id, display_name, shortcode_uuid, operations) "
                + "VALUES (?, ?, ?, ?)", clientId, clientId, shortcode.getUuid(), operations);
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setClientId(clientId);
        subscription.setUrl("https://" + clientId + ".example.test/hooks");
        subscription.setSecret("whsec-test");
        subscription.setEventTypeSet(java.util.Set.of());
        subscriptions.saveAndFlush(subscription);
    }

    private UUID deposit(String receipt, String amount, LocalDateTime at) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO mpesa_incoming_payments (uuid, shortcode_uuid, payment_type, transaction_id, "
                + "phone_number, amount, status, transaction_date, created_at) VALUES (?, ?, 'C2B', ?, '254712345678', "
                + "?::numeric, 'SUCCESS', ?, ?)", uuid, shortcode.getUuid(), receipt, amount, at, at);
        return uuid;
    }

    private void payout(String receipt, String amount, LocalDateTime at) {
        jdbc.update("INSERT INTO mpesa_outgoing_payments (shortcode_uuid, payment_type, transaction_id, "
                + "originator_conversation_id, recipient_phone_number, amount, command_id, initiator_name, status, "
                + "transaction_date, created_at) VALUES (?, 'B2C', ?, ?, '254733000222', ?::numeric, 'BusinessPayment', "
                + "'elimika-api', 'SUCCESS', ?, ?)", shortcode.getUuid(), receipt, "sar-" + UUID.randomUUID(), amount,
                at, at);
    }

    private static ReconciliationExceptionDto find(List<ReconciliationExceptionDto> rows, String receipt) {
        return rows.stream().filter(r -> r.receipt().equals(receipt)).findFirst().orElseThrow();
    }
}
