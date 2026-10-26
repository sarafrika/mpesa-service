package com.sarafrika.apps.mpesaservice.services.callbacks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.models.CallbackInboxEntry;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.CallbackInboxRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaOutgoingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionScope;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Callbacks posted the way Safaricom does land in the inbox on the real migrations, and replaying them
 * goes through the live handlers without settling or announcing a payment twice. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "mpesa.callbacks.inbox.max-body-bytes=2048"})
@AutoConfigureMockMvc
@Testcontainers
class CallbackInboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class Beans {
        @Bean
        Events paymentEvents() {
            return new Events();
        }
    }

    static class Events {
        final Queue<PaymentEvent> all = new ConcurrentLinkedQueue<>();

        @EventListener
        void on(PaymentEvent event) {
            all.add(event);
        }

        long about(UUID payment) {
            return all.stream().filter(e -> payment.equals(e.paymentUuid())).count();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Events events;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CallbackInboxRepository inboxRepository;
    @Autowired
    private CallbackInboxService inboxService;
    @Autowired
    private CallbackReplayService replayService;
    @Autowired
    private CallbackFailuresAttentionSource attentionSource;
    @Autowired
    private MpesaShortCodeRepository shortCodeRepository;
    @Autowired
    private MpesaIncomingPaymentRepository incomingRepository;
    @Autowired
    private MpesaOutgoingPaymentRepository outgoingRepository;

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
        signIn("mpesa-operator");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aC2BConfirmationIsStoredLinkedAndAReplayDoesNotStoreOrAnnounceItAgain() throws Exception {
        String transId = unique("TJ");
        post("/api/v1/callbacks/c2b/confirmation", c2b(transId)).andExpect(jsonPath("$.ResultCode").value("0"));

        CallbackInboxEntry entry = latest(transId);
        MpesaIncomingPayment payment = incomingRepository.findByTransactionId(transId).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(CallbackStatus.PROCESSED);
        assertThat(entry.getKind()).isEqualTo(CallbackKind.C2B_CONFIRMATION);
        assertThat(entry.getPaymentUuid()).isEqualTo(payment.getUuid());
        assertThat(entry.getSourceIp()).isEqualTo("127.0.0.1");
        assertThat(entry.getReference()).contains(transId).contains("ELM-004512");
        assertThat(events.about(payment.getUuid())).isEqualTo(1);

        markFailed(entry.getUuid());
        CallbackDetailDto replayed = replayService.replay(entry.getUuid());

        assertThat(replayed.callback().status()).isEqualTo("REPLAYED");
        assertThat(replayed.callback().attempts()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mpesa_incoming_payments WHERE transaction_id = ?",
                Long.class, transId)).isEqualTo(1);
        assertThat(events.about(payment.getUuid())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'CALLBACK_REPLAYED' "
                + "AND entity_id = ?", Long.class, entry.getUuid().toString())).isEqualTo(1);
    }

    @Test
    void anStkCallbackThatArrivedTooEarlyFailsThenSettlesOnceOnReplay() throws Exception {
        String checkout = unique("ws_CO_");
        post("/api/v1/callbacks/stk/callback", stk(checkout)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("00000001"));
        CallbackInboxEntry failed = latest(checkout);
        assertThat(failed.getStatus()).isEqualTo(CallbackStatus.FAILED);
        assertThat(failed.getError()).startsWith("IllegalArgumentException: No incoming payment found");

        MpesaIncomingPayment pending = new MpesaIncomingPayment();
        pending.setShortcodeUuid(shortcode.getUuid());
        pending.setPaymentType(IncomingPaymentType.STK_PUSH);
        pending.setCheckoutRequestId(checkout);
        pending.setTransactionId(checkout);
        pending.setPhoneNumber("254708374149");
        pending.setAmount(BigDecimal.ONE);
        pending.setAccountReference("ORDER-7731");
        UUID payment = incomingRepository.saveAndFlush(pending).getUuid();

        CallbackDetailDto replayed = replayService.replay(failed.getUuid());
        assertThat(replayed.callback().status()).isEqualTo("REPLAYED");
        assertThat(replayed.callback().paymentUuid()).isEqualTo(payment);
        assertThat(incomingRepository.findByUuid(payment).orElseThrow().getStatus())
                .isEqualTo(IncomingPaymentStatus.SUCCESS);
        assertThat(events.about(payment)).isEqualTo(1);

        markFailed(failed.getUuid());
        replayService.replay(failed.getUuid());
        assertThat(events.about(payment)).isEqualTo(1);
    }

    @Test
    void aB2CResultSettlesThePayoutOnceHoweverOftenItIsReplayed() throws Exception {
        MpesaOutgoingPayment payout = new MpesaOutgoingPayment();
        payout.setOriginatorConversationId(unique("sar-"));
        payout.setShortcodeUuid(shortcode.getUuid());
        payout.setPaymentType(OutgoingPaymentType.B2C);
        payout.setRecipientPhoneNumber("254708374149");
        payout.setAmount(new BigDecimal("1500"));
        payout.setCommandId("BusinessPayment");
        payout.setInitiatorName("elimika-api");
        payout.setStatus(OutgoingPaymentStatus.PENDING);
        payout.setClientId("elimika-backend");
        payout.setClientReference(unique("WD-"));
        payout = outgoingRepository.saveAndFlush(payout);

        post("/api/v1/callbacks/b2c/result", b2c(payout.getOriginatorConversationId()))
                .andExpect(jsonPath("$.ResultCode").value("00000000"));
        CallbackInboxEntry entry = latest(payout.getOriginatorConversationId());
        assertThat(entry.getStatus()).isEqualTo(CallbackStatus.PROCESSED);
        assertThat(entry.getPaymentUuid()).isEqualTo(payout.getUuid());
        assertThat(events.about(payout.getUuid())).isEqualTo(1);

        markFailed(entry.getUuid());
        replayService.replay(entry.getUuid());
        markFailed(entry.getUuid());
        replayService.replay(entry.getUuid());

        assertThat(outgoingRepository.findByUuid(payout.getUuid()).orElseThrow().getStatus())
                .isEqualTo(OutgoingPaymentStatus.SUCCESS);
        assertThat(events.about(payout.getUuid())).isEqualTo(1);
    }

    @Test
    void onlyFailedCallbacksWithAStoredBodyCanBeReplayedAndOnlyOnceAtATime() throws Exception {
        String transId = unique("TK");
        post("/api/v1/callbacks/c2b/confirmation", c2b(transId));
        CallbackInboxEntry processed = latest(transId);
        assertThatThrownBy(() -> replayService.replay(processed.getUuid())).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only failed callbacks");

        post("/api/v1/callbacks/c2b/validation", c2b(transId));
        CallbackInboxEntry validation = inboxRepository.findAll().stream()
                .filter(e -> e.getKind() == CallbackKind.C2B_VALIDATION && transId.equals(firstWord(e.getReference())))
                .findFirst().orElseThrow();
        markFailed(validation.getUuid());
        assertThatThrownBy(() -> replayService.replay(validation.getUuid())).isInstanceOf(ConflictException.class);

        String big = "{\"ConversationID\":\"" + unique("AG_") + "\",\"Pad\":\"" + "x".repeat(3000) + "\"}";
        post("/api/v1/callbacks/b2b/timeout", big).andExpect(status().isOk());
        CallbackInboxEntry truncated = inboxRepository.findAll(Sort.by(Sort.Order.desc("id"))).getFirst();
        assertThat(truncated.isBodyTruncated()).isTrue();
        assertThat(truncated.getBody()).isNull();
        assertThat(truncated.getBodySize()).isGreaterThan(3000);

        markFailed(processed.getUuid());
        assertThat(inboxRepository.claimForReplay(processed.getUuid(), inboxService.now(), inboxService.stuckBefore()))
                .isEqualTo(1);
        assertThat(inboxRepository.claimForReplay(processed.getUuid(), inboxService.now(), inboxService.stuckBefore()))
                .isZero();
    }

    @Test
    void personalDataIsMaskedForViewersAndShownToFinance() throws Exception {
        String transId = unique("TM");
        post("/api/v1/callbacks/c2b/confirmation", c2b(transId));
        UUID entry = latest(transId).getUuid();

        signIn("mpesa-viewer");
        CallbackDetailDto masked = inboxService.get(entry);
        assertThat(masked.body().get("MSISDN").asText()).isEqualTo("2547•••••612");
        assertThat(masked.body().get("FirstName").asText()).isEqualTo("J.");
        assertThat(masked.body().get("TransID").asText()).isEqualTo(transId);
        assertThat(masked.headers()).containsKey("Content-Type");

        signIn("mpesa-finance");
        assertThat(inboxService.get(entry).body().get("MSISDN").asText()).isEqualTo("254712345612");
        assertThat(jdbc.queryForObject("SELECT body->>'MSISDN' FROM callback_inbox WHERE uuid = ?", String.class,
                entry)).isEqualTo("254712345612");
    }

    @Test
    void listFiltersSummaryAndTheAttentionItemReadTheInbox() throws Exception {
        String checkout = unique("ws_CO_");
        post("/api/v1/callbacks/stk/callback", stk(checkout));

        Page<CallbackDto> found = inboxService.list(new CallbackInboxService.Filter("failed", "STK_CALLBACK", null,
                checkout.toLowerCase(), null, null), PageRequests.of(0, 25, null, Map.of(), Sort.by("receivedAt")));
        assertThat(found.getContent()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("FAILED");
            assertThat(row.endpoint()).isEqualTo("/api/v1/callbacks/stk/callback");
        });
        assertThat(inboxService.summary().failed()).isPositive();

        List<AttentionItemDto> items = attentionSource.currentItems(AttentionScope.ALL);
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo("CALLBACKS_FAILING");
            assertThat(item.level()).isEqualTo(AttentionItemDto.CRITICAL);
            assertThat(item.href()).isEqualTo("/callbacks?status=FAILED");
        });
        assertThat(attentionSource.currentItems(new AttentionScope(shortcode.getUuid(), null))).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions post(String path, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private CallbackInboxEntry latest(String reference) {
        return inboxRepository.findAll(Sort.by(Sort.Order.desc("id"))).stream()
                .filter(e -> e.getReference() != null && e.getReference().contains(reference))
                .findFirst().orElseThrow();
    }

    private void markFailed(UUID uuid) {
        jdbc.update("UPDATE callback_inbox SET status = 'FAILED' WHERE uuid = ?", uuid);
    }

    private void signIn(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("tester", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static String firstWord(String text) {
        return text == null ? null : text.split(" ")[0];
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private String c2b(String transId) {
        return """
                {"TransactionType":"Pay Bill","TransID":"%s","TransTime":"20261026143208","TransAmount":"4500.00",
                 "BusinessShortCode":"%s","BillRefNumber":"ELM-004512","MSISDN":"254712345612","FirstName":"JANE",
                 "LastName":"KAMAU"}""".formatted(transId, shortcode.getShortcode());
    }

    private static String stk(String checkout) {
        return """
                {"Body":{"stkCallback":{"MerchantRequestID":"29115-34620561-1","CheckoutRequestID":"%s","ResultCode":0,
                 "ResultDesc":"The service request is processed successfully.","CallbackMetadata":{"Item":[
                 {"Name":"Amount","Value":1.00},{"Name":"MpesaReceiptNumber","Value":"%s"},
                 {"Name":"TransactionDate","Value":20261026143208},{"Name":"PhoneNumber","Value":254708374149}]}}}}"""
                .formatted(checkout, "R" + checkout.substring(6));
    }

    private static String b2c(String originator) {
        return """
                {"Result":{"ResultType":0,"ResultCode":0,"ResultDesc":"The service request is processed successfully.",
                 "OriginatorConversationID":"%s","ConversationID":"AG_20261026_%s","TransactionID":"%s",
                 "ResultParameters":{"ResultParameter":[{"Key":"TransactionAmount","Value":1500},
                 {"Key":"TransactionReceipt","Value":"%s"},
                 {"Key":"ReceiverPartyPublicName","Value":"254708374149 - JANE WANJIRU KAMAU"},
                 {"Key":"B2CUtilityAccountAvailableFunds","Value":10116.00}]}}}"""
                .formatted(originator, originator.substring(4), "Q" + originator.substring(4),
                        "Q" + originator.substring(4));
    }
}
