package com.sarafrika.apps.mpesaservice.services.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.C2BConfirmationCallback;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkStatusResponse;
import com.sarafrika.apps.mpesaservice.dtos.TransactionStatusResponse;
import com.sarafrika.apps.mpesaservice.dtos.TransactionStatusResultData;
import com.sarafrika.apps.mpesaservice.dtos.TransactionStatusResultParameter;
import com.sarafrika.apps.mpesaservice.dtos.TransactionStatusResultParameters;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.StatusQueryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TimelineEventDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TransactionDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TransactionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.UpdateValidationRulesRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationDryRunRequest;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaOutgoingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.impl.MpesaIncomingPaymentServiceImpl;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import com.sarafrika.apps.mpesaservice.utils.enums.C2BChannel;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.LookupFailurePolicy;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** C2B confirmations, the transactions feed, status queries and validation rules on the real migrations. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({C2BConfirmationService.class, TransactionFeedService.class, TransactionStatusService.class,
        ValidationRuleService.class, C2BValidationService.class, AccountLookupClient.class, StkStatusReconciler.class,
        MpesaIncomingPaymentServiceImpl.class, AuditService.class, SecretCipher.class, MpesaPayoutService.class,
        CollectionsIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RecordApplicationEvents
class CollectionsIntegrationTest {

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
        com.sarafrika.apps.mpesaservice.services.security.OutboundUrlGuard outboundUrlGuard() {
            return com.sarafrika.apps.mpesaservice.services.security.TestUrlGuards.publicDns();
        }

        @Bean
        AllEvents allEvents() {
            return new AllEvents();
        }
    }

    /** Events from every thread; ApplicationEvents only records the test thread's. */
    static class AllEvents {
        final java.util.Queue<PaymentEvent> seen = new java.util.concurrent.ConcurrentLinkedQueue<>();

        @org.springframework.context.event.EventListener
        void on(PaymentEvent event) {
            seen.add(event);
        }
    }

    @Autowired
    private AllEvents allEvents;
    @Autowired
    private MpesaIncomingPaymentServiceImpl incomingService;

    @Autowired
    private C2BConfirmationService confirmationService;
    @Autowired
    private TransactionFeedService feedService;
    @Autowired
    private TransactionStatusService statusService;
    @Autowired
    private ValidationRuleService ruleService;
    @Autowired
    private StkStatusReconciler reconciler;
    @Autowired
    private MpesaShortCodeRepository shortCodeRepository;
    @Autowired
    private MpesaIncomingPaymentRepository incomingRepository;
    @Autowired
    private MpesaOutgoingPaymentRepository outgoingRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private MpesaDarajaService darajaService;

    private MpesaShortCode shortcode;

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
        shortcode = shortCodeRepository.saveAndFlush(code);
    }

    // ---------- C2B confirmations ----------

    @Test
    void aRetriedConfirmationIsStoredOnceAndAnnouncedOnce() {
        String transId = unique("TJQ");
        C2BConfirmationCallback callback = confirmation(transId, shortcode.getShortcode(), "Pay Bill");

        C2BConfirmationService.Confirmation first = confirmationService.confirm(callback, Map.of("TransID", transId,
                "TransAmount", "4500.00", "Extra", "kept"));
        C2BConfirmationService.Confirmation again = confirmationService.confirm(callback, null);

        assertThat(first.duplicate()).isFalse();
        assertThat(again.duplicate()).isTrue();
        assertThat(again.payment().getUuid()).isEqualTo(first.payment().getUuid());
        assertThat(jdbc.queryForObject("select count(*) from mpesa_incoming_payments where transaction_id = ?",
                Integer.class, transId)).isEqualTo(1);
        MpesaIncomingPayment stored = incomingRepository.findByTransactionId(transId).orElseThrow();
        assertThat(stored.getShortcodeUuid()).isEqualTo(shortcode.getUuid());
        assertThat(stored.getPaymentType()).isEqualTo(IncomingPaymentType.C2B);
        assertThat(stored.getC2bChannel()).isEqualTo(C2BChannel.PAYBILL);
        assertThat(stored.getAccountReference()).isEqualTo("ELM-004512");
        assertThat(stored.getPhoneNumber()).isEqualTo("254712345612");
        assertThat(stored.getFirstName()).isEqualTo("JANE");
        assertThat(stored.getLastName()).isEqualTo("KAMAU");
        assertThat(stored.getAmount()).isEqualByComparingTo("4500.00");
        assertThat(stored.getTransactionDate()).isEqualTo(LocalDateTime.of(2026, 10, 26, 14, 32, 8));
        assertThat(stored.getStatus()).isEqualTo(IncomingPaymentStatus.SUCCESS);
        assertThat(jdbc.queryForObject("select raw_callback_data ->> 'Extra' from mpesa_incoming_payments "
                + "where transaction_id = ?", String.class, transId)).isEqualTo("kept");

        List<PaymentEvent> published = events.stream(PaymentEvent.class)
                .filter(e -> first.payment().getUuid().equals(e.paymentUuid())).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().type()).isEqualTo(PaymentEvent.C2B_CONFIRMED);
        assertThat(published.getFirst().shortcodeUuid()).isEqualTo(shortcode.getUuid());
        assertThat(published.getFirst().data()).containsEntry("account_reference", "ELM-004512");
    }

    @Test
    void aConfirmationForAnUnknownShortcodeIsKeptUnlinkedAndBuyGoodsIsRecorded() {
        String transId = unique("TJU");
        confirmationService.confirm(confirmation(transId, "999999", "Buy Goods"), null);

        MpesaIncomingPayment stored = incomingRepository.findByTransactionId(transId).orElseThrow();
        assertThat(stored.getShortcodeUuid()).isNull();
        assertThat(stored.getBusinessShortCode()).isEqualTo("999999");
        assertThat(stored.getC2bChannel()).isEqualTo(C2BChannel.BUY_GOODS);

        TransactionDetailDto detail = feedService.detail(stored.getUuid());
        assertThat(detail.transaction().shortcode()).isEqualTo("999999");
        assertThat(detail.canQueryStatus()).isFalse();
        assertThatThrownBy(() -> statusService.request(stored.getUuid())).isInstanceOf(ConflictException.class);
    }

    // ---------- the feed ----------

    @Test
    void theFeedPagesAcrossBothTablesNewestFirst() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 10, 9, 0);
        UUID in1 = c2b("100", base.plusMinutes(1));
        UUID out1 = payout("200", base.plusMinutes(2), OutgoingPaymentStatus.SUCCESS);
        UUID in2 = c2b("300", base.plusMinutes(3));
        UUID out2 = payout("400", base.plusMinutes(4), OutgoingPaymentStatus.PENDING);
        UUID in3 = stk("500", base.plusMinutes(5));
        UUID out3 = payout("600", base.plusMinutes(6), OutgoingPaymentStatus.FAILED);
        TransactionFeedService.Filter mine = filter(null, null, null, null, null);

        Page<TransactionDto> first = feedService.list(mine, page(0, 4, null));
        Page<TransactionDto> second = feedService.list(mine, page(1, 4, null));
        Page<TransactionDto> pastTheEnd = feedService.list(mine, page(5, 4, null));

        assertThat(first.getContent()).extracting(TransactionDto::uuid).containsExactly(out3, in3, out2, in2);
        assertThat(second.getContent()).extracting(TransactionDto::uuid).containsExactly(out1, in1);
        assertThat(first.getTotalElements()).isEqualTo(6);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(pastTheEnd.getContent()).isEmpty();
        assertThat(pastTheEnd.getTotalElements()).isEqualTo(6);
        assertThat(first.getContent().get(0).direction()).isEqualTo("out");
        assertThat(first.getContent().get(1).type()).isEqualTo("STK_PUSH");
        assertThat(first.getContent().get(1).receipt()).isNull();
        assertThat(first.getContent().get(1).createdAt().toLocalDateTime()).isEqualTo(base.plusMinutes(5));

        Page<TransactionDto> byAmount = feedService.list(mine, page(0, 3, List.of("amount,asc")));
        assertThat(byAmount.getContent()).extracting(TransactionDto::uuid).containsExactly(in1, out1, in2);
    }

    @Test
    void theFeedFiltersByDirectionTypeStatusPhoneQueryAndDates() {
        LocalDateTime base = LocalDateTime.of(2026, 2, 10, 9, 0);
        UUID deposit = c2b("100", base);
        UUID payout = payout("200", base.plusHours(1), OutgoingPaymentStatus.PENDING);
        UUID stk = stk("300", base.plusHours(2));
        String receipt = incomingRepository.findByUuid(deposit).orElseThrow().getTransactionId();
        String checkout = incomingRepository.findByUuid(stk).orElseThrow().getCheckoutRequestId();

        assertThat(ids(filter("in", null, null, null, null))).containsExactlyInAnyOrder(deposit, stk);
        assertThat(ids(filter("out", null, null, null, null))).containsExactly(payout);
        assertThat(ids(filter(null, "c2b", null, null, null))).containsExactly(deposit);
        assertThat(ids(filter(null, null, "pending", null, null))).containsExactlyInAnyOrder(payout, stk);
        assertThat(ids(filter(null, null, null, "0712 345 612", null))).containsExactly(deposit);
        assertThat(ids(filter(null, null, null, "+254799000111", null))).containsExactly(payout);
        assertThat(ids(filter(null, null, null, null, receipt))).containsExactly(deposit);
        assertThat(ids(filter(null, null, null, null, checkout))).containsExactly(stk);
        assertThat(ids(filter(null, null, null, null, "elm-0045"))).containsExactly(deposit);
        assertThat(ids(filter(null, null, null, null, "WD-"))).containsExactly(payout);
        assertThat(ids(filter(null, null, null, null, "%"))).isEmpty();

        ZoneId zone = ZoneId.systemDefault();
        TransactionFeedService.Filter window = new TransactionFeedService.Filter(null, null, null, shortcode.getUuid(),
                null, null, base.plusMinutes(30).atZone(zone).toOffsetDateTime(),
                base.plusHours(2).atZone(zone).toOffsetDateTime());
        assertThat(ids(window)).containsExactly(payout);

        assertThatThrownBy(() -> feedService.list(filter("sideways", null, null, null, null), page(0, 10, null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> feedService.list(filter(null, "REVERSAL", null, null, null), page(0, 10, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void aPayoutsDetailHasItsProductTimelineAndRawCallback() {
        UUID uuid = payout("1500", LocalDateTime.of(2026, 3, 1, 8, 0), OutgoingPaymentStatus.SUCCESS);
        jdbc.update("update mpesa_outgoing_payments set raw_callback_data = '{\"Result\":{\"ResultCode\":0}}'::jsonb, "
                + "processed_at = ? where uuid = ?", LocalDateTime.of(2026, 3, 1, 8, 1), uuid);

        TransactionDetailDto detail = feedService.detail(uuid);

        assertThat(detail.transaction().clientId()).isEqualTo("elimika-backend");
        assertThat(detail.rawCallback().path("Result").path("ResultCode").asInt()).isZero();
        assertThat(detail.timeline()).extracting(TimelineEventDto::kind)
                .containsExactly("created", "sent", "result_received");
        assertThat(detail.canQueryStatus()).isTrue();
    }

    // ---------- status queries ----------

    @Test
    void aTransactionStatusResultSettlesAPendingPayoutOnce() {
        UUID uuid = payout("1500", LocalDateTime.now().minusMinutes(30), OutgoingPaymentStatus.PENDING);
        when(darajaService.queryTransactionStatus(any(MpesaShortCode.class), any(), any(), any()))
                .thenReturn(MpesaApiResponse.success(new TransactionStatusResponse("AG_TS_" + uuid, "TS-" + uuid,
                        "Accept the service request successfully."), 200));

        StatusQueryDto query = statusService.request(uuid);
        assertThat(query.status()).isEqualTo("PENDING");
        assertThat(query.method()).isEqualTo("TRANSACTION_STATUS");

        TransactionStatusResultData result = new TransactionStatusResultData(0, 0, "Processed", "TS-" + uuid,
                "AG_TS_" + uuid, "TS1", new TransactionStatusResultParameters(List.of(
                new TransactionStatusResultParameter("ReceiptNo", "TJS" + uuid.toString().substring(0, 7).toUpperCase()),
                new TransactionStatusResultParameter("TransactionStatus", "Completed"))));
        statusService.applyResult(result, "{\"Result\":{}}");
        statusService.applyResult(result, "{\"Result\":{}}");

        MpesaOutgoingPayment settled = outgoingRepository.findByUuid(uuid).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(OutgoingPaymentStatus.SUCCESS);
        assertThat(settled.getTransactionId()).startsWith("TJS");
        assertThat(events.stream(PaymentEvent.class).filter(e -> uuid.equals(e.paymentUuid())))
                .extracting(PaymentEvent::type).containsExactly(PaymentEvent.B2C_COMPLETED);
        TransactionDetailDto detail = feedService.detail(uuid);
        assertThat(detail.statusQueries()).singleElement()
                .satisfies(q -> assertThat(q.transactionStatus()).isEqualTo("Completed"));
        assertThat(detail.timeline()).extracting(TimelineEventDto::kind).contains("status_query", "status_answer");
    }

    @Test
    void safaricomFailuresBecomeUpstreamErrors() {
        UUID uuid = payout("1500", LocalDateTime.now(), OutgoingPaymentStatus.PENDING);
        when(darajaService.queryTransactionStatus(any(MpesaShortCode.class), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error("INTERNAL_ERROR", "I/O error on POST", 500));
        assertThatThrownBy(() -> statusService.request(uuid)).isInstanceOfSatisfying(UpstreamException.class,
                e -> assertThat(e.isUnavailable()).isTrue());

        when(darajaService.queryTransactionStatus(any(MpesaShortCode.class), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error("CLIENT_ERROR", "Client error: 400 BAD_REQUEST", 400));
        assertThatThrownBy(() -> statusService.request(uuid)).isInstanceOfSatisfying(UpstreamException.class,
                e -> assertThat(e.isUnavailable()).isFalse());
    }

    @Test
    void theStkJobSettlesOldPendingRequestsAndLeavesFreshOnes() {
        UUID old = stk("250", LocalDateTime.now().minusMinutes(30));
        UUID fresh = stk("250", LocalDateTime.now().minusMinutes(1));
        String oldCheckout = incomingRepository.findByUuid(old).orElseThrow().getCheckoutRequestId();
        when(darajaService.queryStkPushStatus(eq(shortcode.getUuid()), eq(oldCheckout)))
                .thenReturn(MpesaApiResponse.success(new StkStatusResponse("m-1", oldCheckout, 1032,
                        "Request cancelled by user", null, null, null, null), 200));

        reconciler.reconcile();

        assertThat(incomingRepository.findByUuid(old).orElseThrow().getStatus()).isEqualTo(IncomingPaymentStatus.CANCELLED);
        assertThat(incomingRepository.findByUuid(fresh).orElseThrow().getStatus()).isEqualTo(IncomingPaymentStatus.PENDING);
        assertThat(events.stream(PaymentEvent.class).filter(e -> old.equals(e.paymentUuid())))
                .extracting(PaymentEvent::type).containsExactly(PaymentEvent.STK_CANCELLED);
    }

    @Test
    void twoAnswersForOneStkRequestArrivingTogetherSettleItOnce() throws Exception {
        UUID uuid = stk("250", LocalDateTime.now().minusMinutes(30));
        String checkout = incomingRepository.findByUuid(uuid).orElseThrow().getCheckoutRequestId();
        Map<String, Object> paid = stkSuccess(checkout, unique("TK"));
        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<?>> runs = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(pool.submit(() -> {
                    start.await();
                    return incomingService.processStkPushCallback(checkout, paid);
                }));
            }
            for (java.util.concurrent.Future<?> run : runs) {
                run.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        incomingService.processStkPushCallback(checkout, Map.of("ResultCode", 1032, "ResultDesc", "Cancelled"));

        assertThat(incomingRepository.findByUuid(uuid).orElseThrow().getStatus()).isEqualTo(IncomingPaymentStatus.SUCCESS);
        assertThat(allEvents.seen.stream().filter(e -> uuid.equals(e.paymentUuid())))
                .extracting(PaymentEvent::type).containsExactly(PaymentEvent.STK_COMPLETED);
    }

    @Test
    void stillProcessingIsNoAnswerAndTheJobBacksOff() {
        UUID uuid = stk("250", LocalDateTime.now().minusMinutes(30));
        String checkout = incomingRepository.findByUuid(uuid).orElseThrow().getCheckoutRequestId();
        when(darajaService.queryStkPushStatus(eq(shortcode.getUuid()), eq(checkout)))
                .thenReturn(MpesaApiResponse.success(new StkStatusResponse("m-1", checkout, 4999,
                        "The transaction is still under processing", null, null, null, null), 200));

        reconciler.reconcile();
        reconciler.reconcile();

        MpesaIncomingPayment after = incomingRepository.findByUuid(uuid).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(IncomingPaymentStatus.PENDING);
        assertThat(after.getStatusChecks()).isEqualTo(1);
        assertThat(after.getNextCheckAt()).isAfter(LocalDateTime.now());
        assertThatThrownBy(() -> statusService.request(uuid)).isInstanceOf(UpstreamException.class);
    }

    @Test
    void aC2bConfirmationThatBeatTheStkCallbackIsMergedIntoTheStkPayment() {
        UUID uuid = stk("4500.00", LocalDateTime.now().minusMinutes(2));
        String checkout = incomingRepository.findByUuid(uuid).orElseThrow().getCheckoutRequestId();
        String receipt = unique("TM");
        UUID c2b = confirmationService.confirm(confirmation(receipt, shortcode.getShortcode(), "Pay Bill"), null)
                .payment().getUuid();
        jdbc.update("INSERT INTO webhook_subscriptions (uuid, client_id, url, secret) VALUES (?, 'x', 'https://x', 's')",
                UUID.randomUUID());
        UUID subscription = jdbc.queryForObject("SELECT uuid FROM webhook_subscriptions ORDER BY id DESC LIMIT 1",
                UUID.class);
        jdbc.update("INSERT INTO webhook_deliveries (subscription_uuid, client_id, event_id, event_type, payload, "
                + "payment_uuid) VALUES (?, 'x', ?, 'c2b.confirmed', '{}', ?)", subscription, UUID.randomUUID(), c2b);

        MpesaIncomingPayment merged = incomingService.processStkPushCallback(checkout, stkSuccess(checkout, receipt));

        assertThat(merged.getUuid()).isEqualTo(uuid);
        assertThat(merged.getTransactionId()).isEqualTo(receipt);
        assertThat(merged.getFirstName()).isEqualTo("JANE");
        assertThat(merged.getRawCallbackData()).containsKey("C2BConfirmation");
        assertThat(incomingRepository.findByUuid(c2b)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM webhook_deliveries WHERE payment_uuid = ?", Long.class,
                uuid)).isEqualTo(1);
    }

    @Test
    void aC2bConfirmationAfterTheStkCallbackOnlyAddsItsDetails() {
        UUID uuid = stk("4500.00", LocalDateTime.now().minusMinutes(2));
        String checkout = incomingRepository.findByUuid(uuid).orElseThrow().getCheckoutRequestId();
        String receipt = unique("TN");
        incomingService.processStkPushCallback(checkout, stkSuccess(checkout, receipt));

        C2BConfirmationService.Confirmation again = confirmationService.confirm(
                confirmation(receipt, shortcode.getShortcode(), "Pay Bill"), Map.of("TransID", receipt));

        assertThat(again.duplicate()).isTrue();
        MpesaIncomingPayment stored = incomingRepository.findByUuid(uuid).orElseThrow();
        assertThat(stored.getLastName()).isEqualTo("KAMAU");
        assertThat(stored.getRawCallbackData()).containsKey("C2BConfirmation");
    }

    private static Map<String, Object> stkSuccess(String checkout, String receipt) {
        return Map.of("CheckoutRequestID", checkout, "ResultCode", 0, "ResultDesc", "Processed",
                "CallbackMetadata", Map.of("Item", List.of(Map.of("Name", "MpesaReceiptNumber", "Value", receipt))));
    }

    // ---------- validation rules ----------

    @Test
    void validationRulesAreSavedAuditedAndDryRun() {
        assertThat(ruleService.get(shortcode.getUuid()).configured()).isFalse();

        ruleService.update(shortcode.getUuid(), new UpdateValidationRulesRequest(true, "^ELM-[0-9]{6}$",
                new BigDecimal("10"), new BigDecimal("250000"), null, null, LookupFailurePolicy.REJECT));

        assertThat(ruleService.get(shortcode.getUuid()).accountPattern()).isEqualTo("^ELM-[0-9]{6}$");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = ? and entity_id = ?",
                Integer.class, ValidationRuleService.AUDIT_ACTION, shortcode.getUuid().toString())).isEqualTo(1);
        assertThat(ruleService.dryRun(shortcode.getUuid(),
                new ValidationDryRunRequest("ELM-004512", new BigDecimal("500"), null, null)).accepted()).isTrue();
        assertThat(ruleService.dryRun(shortcode.getUuid(),
                new ValidationDryRunRequest("X1", new BigDecimal("500"), null, null)).resultCode())
                .isEqualTo(C2BResultCodes.INVALID_ACCOUNT);
        assertThatThrownBy(() -> ruleService.update(shortcode.getUuid(), new UpdateValidationRulesRequest(true,
                "^(ELM", null, null, null, null, LookupFailurePolicy.REJECT)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("account_pattern"));
    }

    // ---------- helpers ----------

    private List<UUID> ids(TransactionFeedService.Filter filter) {
        return feedService.list(filter, page(0, 100, null)).getContent().stream().map(TransactionDto::uuid).toList();
    }

    private TransactionFeedService.Filter filter(String direction, String type, String status, String phone, String q) {
        return new TransactionFeedService.Filter(direction, type, status, shortcode.getUuid(), phone, q, null, null);
    }

    private static org.springframework.data.domain.PageRequest page(int page, int size, List<String> sort) {
        return PageRequests.of(page, size, sort, Map.of("created_at", "createdAt", "amount", "amount"),
                Sort.by(Sort.Order.desc("createdAt")));
    }

    private UUID c2b(String amount, LocalDateTime createdAt) {
        String transId = unique("TJC");
        C2BConfirmationCallback callback = new C2BConfirmationCallback("Pay Bill", transId, "20261026143208",
                new BigDecimal(amount), shortcode.getShortcode(), "ELM-004512", null, null, null, "254712345612",
                "JANE", null, "KAMAU");
        UUID uuid = confirmationService.confirm(callback, null).payment().getUuid();
        backdate("mpesa_incoming_payments", uuid, createdAt);
        return uuid;
    }

    private UUID stk(String amount, LocalDateTime createdAt) {
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        String checkout = unique("ws_CO_");
        payment.setShortcodeUuid(shortcode.getUuid());
        payment.setPaymentType(IncomingPaymentType.STK_PUSH);
        payment.setCheckoutRequestId(checkout);
        payment.setTransactionId(checkout);
        payment.setPhoneNumber("254722000333");
        payment.setAmount(new BigDecimal(amount));
        payment.setAccountReference("ORDER-7731");
        payment.setClientId("shop-api");
        UUID uuid = incomingRepository.saveAndFlush(payment).getUuid();
        backdate("mpesa_incoming_payments", uuid, createdAt);
        return uuid;
    }

    private UUID payout(String amount, LocalDateTime createdAt, OutgoingPaymentStatus status) {
        MpesaOutgoingPayment payout = new MpesaOutgoingPayment();
        payout.setShortcodeUuid(shortcode.getUuid());
        payout.setPaymentType(OutgoingPaymentType.B2C);
        payout.setOriginatorConversationId(unique("sar-"));
        payout.setConversationId(unique("AG_"));
        payout.setRecipientPhoneNumber("254799000111");
        payout.setAmount(new BigDecimal(amount));
        payout.setCommandId("BusinessPayment");
        payout.setInitiatorName("elimika-api");
        payout.setClientId("elimika-backend");
        payout.setClientReference(unique("WD-"));
        payout.setStatus(status);
        UUID uuid = outgoingRepository.saveAndFlush(payout).getUuid();
        backdate("mpesa_outgoing_payments", uuid, createdAt);
        return uuid;
    }

    private void backdate(String table, UUID uuid, LocalDateTime createdAt) {
        jdbc.update("update " + table + " set created_at = ? where uuid = ?", createdAt, uuid);
    }

    private static C2BConfirmationCallback confirmation(String transId, String shortcode, String transType) {
        return new C2BConfirmationCallback(transType, transId, "20261026143208", new BigDecimal("4500.00"), shortcode,
                "ELM-004512", "", new BigDecimal("1640210.00"), null, "254712345612", "JANE", "W", "KAMAU");
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
