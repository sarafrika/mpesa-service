package com.sarafrika.apps.mpesaservice.services.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkStatusResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationExceptionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationRunDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.StkCandidateDto;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.collections.C2BConfirmationService;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionStatusService;
import com.sarafrika.apps.mpesaservice.services.features.ShortcodeFeatures;
import com.sarafrika.apps.mpesaservice.services.impl.MpesaIncomingPaymentServiceImpl;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** An STK payment whose callback was lost is settled by a status query, whose reply carries no receipt. The
 * statement then shows its receipt: the receipt is attached to that payment, never backfilled as a second one. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReconciliationService.class, ReconciliationResolver.class, ReconciliationLedger.class, StkCandidates.class,
        PullTransactionsService.class, ReconciliationConfig.class, ShortcodeFeatures.class, C2BConfirmationService.class,
        TransactionStatusService.class, MpesaIncomingPaymentServiceImpl.class, AuditService.class, SecretCipher.class,
        StkReceiptAttachIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StkReceiptAttachIntegrationTest {

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
    private TransactionStatusService statusService;
    @Autowired
    private MpesaShortCodeRepository shortcodes;
    @Autowired
    private MpesaIncomingPaymentRepository payments;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Events events;
    @MockitoBean
    private MpesaDarajaService daraja;
    @MockitoBean
    private MpesaPayoutService payouts;

    private MpesaShortCode shortcode;
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
        shortcode = shortcodes.saveAndFlush(code);
        events.all.clear();
        p = UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
    }

    @Test
    void aStatusQuerySettledStkPaymentGetsTheStatementReceiptAndIsNeverBackfilledTwice() throws Exception {
        LocalDateTime requested = LocalDateTime.of(2026, 10, 25, 10, 1, 10);
        UUID stk = stkPush("ws_CO_" + p + "A", "254722123111", requested);
        stkPush("ws_CO_" + p + "B", "254799000999", requested); // same amount, another phone: not this deposit
        // The STK callback never came; the console asks Safaricom, whose documented reply has no receipt.
        for (String checkout : List.of("ws_CO_" + p + "A", "ws_CO_" + p + "B")) {
            when(daraja.queryStkPushStatus(eq(shortcode.getUuid()), eq(checkout))).thenReturn(MpesaApiResponse.success(
                    new StkStatusResponse("22205-34066-1", checkout, 0, "The service request is processed successfully.",
                            null, null, null, null), 200));
        }
        statusService.request(stk);
        MpesaIncomingPayment settled = payments.findByUuid(stk).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(IncomingPaymentStatus.SUCCESS);
        assertThat(settled.getTransactionId()).isEqualTo(settled.getCheckoutRequestId());
        assertThat(events.all).extracting(PaymentEvent::type).containsExactly(PaymentEvent.STK_COMPLETED);
        statusService.request(payments.findByTransactionId("ws_CO_" + p + "B").orElseThrow().getUuid());
        events.all.clear();

        ReconciliationRunDto run = upload();
        ReconciliationExceptionDto missing = service.listExceptions(run.uuid(), null, null, PageRequest.of(0, 25))
                .getContent().stream().filter(e -> e.receipt().equals(p + "0BB")).findFirst().orElseThrow();

        assertThat(missing.allowedActions()).containsExactly("ATTACH_STK_RECEIPT", "MARK_RESOLVED");
        assertThat(missing.stkCandidates()).isEqualTo(1);
        assertThatThrownBy(() -> resolver.backfill(missing.uuid())).isInstanceOf(ConflictException.class)
                .hasMessageContaining("attach the receipt");
        List<StkCandidateDto> candidates = resolver.listStkCandidates(missing.uuid());
        assertThat(candidates).extracting(StkCandidateDto::paymentUuid).containsExactly(stk);
        assertThat(candidates.getFirst().phoneNumber()).isEqualTo("2547•••••111");
        assertThatThrownBy(() -> resolver.attachStkReceipt(missing.uuid(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);

        ReconciliationExceptionDto done = resolver.attachStkReceipt(missing.uuid(), stk);

        assertThat(done.status()).isEqualTo("RESOLVED");
        assertThat(done.lastAction()).isEqualTo("ATTACH_STK_RECEIPT");
        assertThat(done.ledgerPaymentUuid()).isEqualTo(stk);
        MpesaIncomingPayment attached = payments.findByUuid(stk).orElseThrow();
        assertThat(attached.getTransactionId()).isEqualTo(p + "0BB");
        assertThat(attached.getTransactionDate()).isEqualTo(LocalDateTime.of(2026, 10, 25, 10, 1, 44));
        assertThat(attached.getRawCallbackData()).containsKey("ReceiptFromReconciliation");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mpesa_incoming_payments WHERE amount = 2500 "
                + "AND shortcode_uuid = ? AND deleted_at IS NULL", Long.class, shortcode.getUuid())).isEqualTo(2);
        assertThat(events.all).isEmpty(); // the product heard of it when it settled; nothing more is announced
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = "
                + "'RECONCILIATION_STK_RECEIPT_ATTACHED' AND entity_id = ?", Long.class, missing.uuid().toString()))
                .isEqualTo(1);

        // The next run over the day finds the receipt in the ledger and offers nothing for it.
        ReconciliationRunDto next = upload();
        assertThat(service.listExceptions(next.uuid(), null, null, PageRequest.of(0, 25)).getContent())
                .extracting(ReconciliationExceptionDto::receipt).doesNotContain(p + "0BB");
    }

    @Test
    void phoneDigitsShownOnTheStatementMustAgreeWithOurs() {
        assertThat(StkCandidates.phoneFits("254722***111 - John ***", "254722123111")).isTrue();
        assertThat(StkCandidates.phoneFits("254722***111 - John ***", "0722123111")).isTrue();
        assertThat(StkCandidates.phoneFits("254722***111 - John ***", "254799000999")).isFalse();
        assertThat(StkCandidates.phoneFits("254722000001", "254722000002")).isFalse();
        assertThat(StkCandidates.phoneFits("601315 - Safaricom1338", "254722000002")).isTrue();
        assertThat(StkCandidates.phoneFits(null, "254722000002")).isTrue();
    }

    private UUID stkPush(String checkout, String phone, LocalDateTime at) {
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setShortcodeUuid(shortcode.getUuid());
        payment.setPaymentType(IncomingPaymentType.STK_PUSH);
        payment.setPhoneNumber(phone);
        payment.setAmount(new BigDecimal("2500.00"));
        payment.setAccountReference("ELM-003019");
        payment.setCheckoutRequestId(checkout);
        payment.setMerchantRequestId("22205-34066-1");
        payment.setTransactionId(checkout);
        payment.setStatus(IncomingPaymentStatus.PENDING);
        payment.setClientId("elimika-backend");
        MpesaIncomingPayment saved = payments.saveAndFlush(payment);
        jdbc.update("UPDATE mpesa_incoming_payments SET created_at = ? WHERE uuid = ?", at, saved.getUuid());
        return saved.getUuid();
    }

    private ReconciliationRunDto upload() throws Exception {
        byte[] content = new String(StatementCsvParserTest.fixture("org-statement-utility.csv"), StandardCharsets.UTF_8)
                .replace("Short Code:,600100", "Short Code:," + shortcode.getShortcode()).replace("TJP9X2K", p)
                .getBytes(StandardCharsets.UTF_8);
        return service.reconcileStatement(shortcode.getUuid(), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25),
                "statement.csv", content);
    }
}
