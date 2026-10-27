package com.sarafrika.apps.mpesaservice.services.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import com.sarafrika.apps.mpesaservice.dtos.B2CPaymentResponse;
import com.sarafrika.apps.mpesaservice.dtos.B2CPayoutRequest;
import com.sarafrika.apps.mpesaservice.dtos.B2CResult;
import com.sarafrika.apps.mpesaservice.dtos.B2CResultParameter;
import com.sarafrika.apps.mpesaservice.dtos.B2CResultParameters;
import com.sarafrika.apps.mpesaservice.dtos.B2CTimeoutCallback;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** A payout is saved before Safaricom is called, paid once per reference, and settled once by its result. */
// Real Flyway migrations and Hibernate validate, as in every deployed environment (test config uses H2).
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MpesaPayoutService.class, SecretCipher.class, AuditService.class, MpesaPayoutServiceIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RecordApplicationEvents
class MpesaPayoutServiceIntegrationTest {

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
        SettledEvents settledEvents() {
            return new SettledEvents();
        }
    }

    /** Counts events from every thread; ApplicationEvents only sees the test thread's. */
    static class SettledEvents {
        final java.util.Queue<PaymentEvent> all = new java.util.concurrent.ConcurrentLinkedQueue<>();

        @org.springframework.context.event.EventListener
        void on(PaymentEvent event) {
            all.add(event);
        }
    }

    @Autowired
    private SettledEvents settledEvents;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private MpesaPayoutService service;
    @Autowired
    private MpesaShortCodeRepository shortCodeRepository;

    @MockitoBean
    private MpesaDarajaService darajaService;
    @Autowired
    private ApplicationEvents events;

    private MpesaShortCode shortcode;

    @BeforeEach
    void setUp() {
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(String.valueOf(600000 + (int) (Math.random() * 399999)));
        code.setShortcodeType(ShortcodeType.ONE_ACCOUNT);
        code.setBusinessName("Elimika payouts");
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setCallbackUrl("https://example.test/api/v1/callbacks/stk/callback");
        code.setEnvironment(Environment.SANDBOX);
        code.setInitiatorName("elimika-api");
        shortcode = shortCodeRepository.saveAndFlush(code);
    }

    @Test
    void savesThePayoutThenRecordsSafaricomsAcceptance() {
        accept();

        MpesaOutgoingPayment payment = service.initiate("elimika-backend", request("WD-1", "1500"));

        assertThat(payment.getStatus()).isEqualTo(OutgoingPaymentStatus.PENDING);
        assertThat(payment.getConversationId()).isEqualTo("AG_1");
        assertThat(payment.getOriginatorConversationId()).startsWith("sar-");
        assertThat(payment.getInitiatorName()).isEqualTo("elimika-api");
    }

    @Test
    void theSameReferenceNeverPaysTwice() {
        accept();

        MpesaOutgoingPayment first = service.initiate("elimika-backend", request("WD-2", "1500"));
        MpesaOutgoingPayment again = service.initiate("elimika-backend", request("WD-2", "1500"));
        MpesaOutgoingPayment otherClient = service.initiate("other-app", request("WD-2", "1500"));

        assertThat(again.getUuid()).isEqualTo(first.getUuid());
        assertThat(otherClient.getUuid()).isNotEqualTo(first.getUuid());
        verify(darajaService, times(2)).sendB2CPayout(any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void aRefusedRequestEndsFailedWithSafaricomsReason() {
        when(darajaService.sendB2CPayout(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error("API_ERROR", "Invalid Initiator Information", 400));

        MpesaOutgoingPayment payment = service.initiate("elimika-backend", request("WD-3", "1500"));

        assertThat(payment.getStatus()).isEqualTo(OutgoingPaymentStatus.FAILED);
        assertThat(payment.getResultDesc()).isEqualTo("Invalid Initiator Information");
    }

    @Test
    void badRequestsAreRefusedBeforeSafaricomIsCalled() {
        assertThatThrownBy(() -> service.initiate("elimika-backend", request("WD-4", "1500.50")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.initiate("elimika-backend",
                new B2CPayoutRequest(shortcode.getUuid(), "0712345678", new BigDecimal("100"), "WD-5", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(darajaService, never()).sendB2CPayout(any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void aSuccessfulResultSettlesItOnceWithReceiptNameAndFloat() {
        accept();
        MpesaOutgoingPayment payment = service.initiate("elimika-backend", request("WD-6", "1500"));

        service.applyResult(success(payment.getOriginatorConversationId()), "{\"Result\":\"raw\"}");
        MpesaOutgoingPayment settled = service.findForClient("elimika-backend", "WD-6").orElseThrow();
        service.applyResult(failure(payment.getOriginatorConversationId()), "{}");

        MpesaOutgoingPayment after = service.findForClient("elimika-backend", payment.getUuid()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(OutgoingPaymentStatus.SUCCESS);
        assertThat(after.getStatus()).isEqualTo(OutgoingPaymentStatus.SUCCESS);
        assertThat(after.getTransactionId()).isEqualTo("TJE4K9L2QX");
        assertThat(after.getRecipientName()).isEqualTo("JANE WANJIRU KAMAU");
        assertThat(after.getChargesPaid()).isEqualByComparingTo("15.00");
        assertThat(service.latestFloat(shortcode.getUuid()).utilityAccountAvailableFunds()).isEqualByComparingTo("98500.00");
    }

    @Test
    void aFailedResultEndsFailedAndATimeoutOnlyFlagsIt() {
        accept();
        MpesaOutgoingPayment failed = service.initiate("elimika-backend", request("WD-7", "1500"));
        MpesaOutgoingPayment waiting = service.initiate("elimika-backend", request("WD-8", "1500"));

        service.applyResult(failure(failed.getOriginatorConversationId()), "{}");
        service.applyTimeout(new B2CTimeoutCallback(0, 1, "The service request has timed out.",
                waiting.getOriginatorConversationId(), "AG_1"));

        assertThat(service.findForClient("elimika-backend", "WD-7").orElseThrow().getStatus())
                .isEqualTo(OutgoingPaymentStatus.FAILED);
        MpesaOutgoingPayment timedOut = service.findForClient("elimika-backend", "WD-8").orElseThrow();
        assertThat(timedOut.getStatus()).isEqualTo(OutgoingPaymentStatus.PENDING);
        assertThat(timedOut.getTimedOutAt()).isNotNull();
    }

    @Test
    void settlingAPayoutPublishesOneEventForThePayingClient() {
        accept();
        MpesaOutgoingPayment paid = service.initiate("elimika-backend", request("WD-9", "1500"));
        MpesaOutgoingPayment refused = service.initiate("elimika-backend", request("WD-10", "1500"));

        B2CResult result = new B2CResult(0, 0, "The service request is processed successfully.",
                paid.getOriginatorConversationId(), "AG_9", "TJE4K9L2Q9", new B2CResultParameters(List.of(
                new B2CResultParameter("TransactionReceipt", "TJE4K9L2Q9"))));
        service.applyResult(result, "{}");
        service.applyResult(result, "{}");
        service.applyResult(failure(refused.getOriginatorConversationId()), "{}");

        List<PaymentEvent> published = events.stream(PaymentEvent.class).toList();
        assertThat(published).extracting(PaymentEvent::type)
                .containsExactly(PaymentEvent.B2C_COMPLETED, PaymentEvent.B2C_FAILED);
        assertThat(published.get(0).clientId()).isEqualTo("elimika-backend");
        assertThat(published.get(0).paymentUuid()).isEqualTo(paid.getUuid());
        assertThat(published.get(0).shortcodeUuid()).isEqualTo(shortcode.getUuid());
        assertThat(published.get(0).data()).containsEntry("client_reference", "WD-9")
                .containsEntry("transaction_id", "TJE4K9L2Q9");
    }

    @Test
    void noAnswerFromSafaricomKeepsThePayoutPendingAndFlagged() {
        when(darajaService.sendB2CPayout(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error("NETWORK_ERROR", "Read timed out", 503));

        MpesaOutgoingPayment payment = service.initiate("elimika-backend", request("WD-20", "1500"));

        assertThat(payment.getStatus()).isEqualTo(OutgoingPaymentStatus.PENDING);
        assertThat(payment.isOutcomeUnknown()).isTrue();
        assertThat(payment.getResultDesc()).contains("do not send it again");

        when(darajaService.sendB2CPayout(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error("SERVER_ERROR", "Server error: 500", 502));
        assertThat(service.initiate("elimika-backend", request("WD-21", "1500")).isOutcomeUnknown()).isTrue();
        when(darajaService.sendB2CPayout(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error("NOT_SENT", "No certificate", 422));
        assertThat(service.initiate("elimika-backend", request("WD-22", "1500")).getStatus())
                .isEqualTo(OutgoingPaymentStatus.FAILED);

        service.applyResult(success(payment.getOriginatorConversationId(), "TJE4K9L220"), "{}");
        MpesaOutgoingPayment paid = service.findForClient("elimika-backend", "WD-20").orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(OutgoingPaymentStatus.SUCCESS);
        assertThat(paid.isOutcomeUnknown()).isFalse();
    }

    @Test
    void aTimeoutFlagsTheOutcomeUnknown() {
        accept();
        MpesaOutgoingPayment waiting = service.initiate("elimika-backend", request("WD-23", "1500"));
        service.applyTimeout(new B2CTimeoutCallback(0, 1, "The service request has timed out.",
                waiting.getOriginatorConversationId(), "AG_1"));
        assertThat(service.findForClient("elimika-backend", "WD-23").orElseThrow().isOutcomeUnknown()).isTrue();
    }

    @Test
    void aSuccessAfterASubmitRefusalSettlesItPaidAuditedWithOnlyACompletedEvent() {
        when(darajaService.sendB2CPayout(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error("400.002.02", "Bad Request - Invalid Amount", 400));
        MpesaOutgoingPayment refused = service.initiate("elimika-backend", request("WD-24", "1500"));
        assertThat(refused.getStatus()).isEqualTo(OutgoingPaymentStatus.FAILED);

        service.applyResult(success(refused.getOriginatorConversationId(), "TJE4K9L224"), "{\"Result\":{}}");
        service.applyResult(success(refused.getOriginatorConversationId(), "TJE4K9L224"), "{\"Result\":{}}");

        MpesaOutgoingPayment paid = service.findForClient("elimika-backend", "WD-24").orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(OutgoingPaymentStatus.SUCCESS);
        assertThat(paid.getTransactionId()).isEqualTo("TJE4K9L224");
        assertThat(events.stream(PaymentEvent.class).filter(e -> refused.getUuid().equals(e.paymentUuid())))
                .extracting(PaymentEvent::type).containsExactly(PaymentEvent.B2C_COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE entity_id = ? AND action = ?",
                Long.class, refused.getUuid().toString(), MpesaPayoutService.AUDIT_LATE_SUCCESS)).isEqualTo(1);
    }

    @Test
    void twoCopiesOfOneResultArrivingTogetherSettleItOnce() throws Exception {
        accept();
        MpesaOutgoingPayment payment = service.initiate("elimika-backend", request("WD-25", "1500"));
        B2CResult result = success(payment.getOriginatorConversationId(), "TJE4K9L225");
        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<?>> runs = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(pool.submit(() -> {
                    start.await();
                    return service.applyResult(result, "{}");
                }));
            }
            for (java.util.concurrent.Future<?> run : runs) {
                run.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(settledEvents.all.stream().filter(e -> payment.getUuid().equals(e.paymentUuid())))
                .extracting(PaymentEvent::type).containsExactly(PaymentEvent.B2C_COMPLETED);
    }

    private void accept() {
        when(darajaService.sendB2CPayout(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.success(new B2CPaymentResponse("AG_1", "orig", "0",
                        "Accept the service request successfully."), 200));
    }

    private B2CPayoutRequest request(String reference, String amount) {
        return new B2CPayoutRequest(shortcode.getUuid(), "254712345678", new BigDecimal(amount), reference,
                "Elimika withdrawal", null);
    }

    private static B2CResult success(String originator) {
        return success(originator, "TJE4K9L2QX");
    }

    private static B2CResult success(String originator, String receipt) {
        return new B2CResult(0, 0, "The service request is processed successfully.", originator, "AG_1", receipt,
                new B2CResultParameters(List.of(
                        new B2CResultParameter("TransactionReceipt", receipt),
                        new B2CResultParameter("TransactionAmount", "1500"),
                        new B2CResultParameter("B2CChargesPaidAmount", "15.00"),
                        new B2CResultParameter("ReceiverPartyPublicName", "254712345678 - JANE WANJIRU KAMAU"),
                        new B2CResultParameter("TransactionCompletedDateTime", "23.10.2026 14:12:03"),
                        new B2CResultParameter("B2CUtilityAccountAvailableFunds", "98500.00"),
                        new B2CResultParameter("B2CWorkingAccountAvailableFunds", "0.00"))));
    }

    private static B2CResult failure(String originator) {
        return new B2CResult(0, 2001, "The initiator information is invalid.", originator, "AG_1", null, null);
    }
}
