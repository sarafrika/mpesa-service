package com.sarafrika.apps.mpesaservice.services.collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.C2BConfirmationCallback;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TransactionDto;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.impl.MpesaIncomingPaymentServiceImpl;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationConfig;
import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationLedger;
import com.sarafrika.apps.mpesaservice.services.reconciliation.StkCandidates;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.DarajaTime;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The service on a JVM whose default zone is neither UTC nor Nairobi (the JDBC session takes it too): Safaricom's
 * times still land on the right instant, and a deposit still finds the STK payment it settles. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({C2BConfirmationService.class, TransactionFeedService.class, TransactionStatusService.class,
        StkStatusReconciler.class, MpesaIncomingPaymentServiceImpl.class, AuditService.class, SecretCipher.class,
        MpesaPayoutService.class, StkCandidates.class, ReconciliationLedger.class, ReconciliationConfig.class,
        NonUtcJvmIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NonUtcJvmIntegrationTest {

    private static final TimeZone ORIGINAL = TimeZone.getDefault();

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
    }

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
    }

    @Autowired
    private C2BConfirmationService confirmations;
    @Autowired
    private TransactionFeedService feed;
    @Autowired
    private MpesaShortCodeRepository shortcodes;
    @Autowired
    private MpesaIncomingPaymentRepository payments;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private MpesaDarajaService daraja;

    private MpesaShortCode shortcode;

    @AfterAll
    static void restoreZone() {
        TimeZone.setDefault(ORIGINAL);
    }

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
    }

    @Test
    void transTimeIsStoredAsTheInstantItNamesInNairobi() {
        String transId = "UF" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        confirmations.confirm(confirmation(transId, "20260602171907", "100.00"), null);

        MpesaIncomingPayment stored = payments.findByTransactionId(transId).orElseThrow();
        assertThat(stored.getTransactionDate()).isEqualTo(Instant.parse("2026-06-02T14:19:07Z"));
        assertThat(jdbc.queryForObject("SELECT to_char(transaction_date AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') "
                + "FROM mpesa_incoming_payments WHERE transaction_id = ?", String.class, transId))
                .isEqualTo("2026-06-02 14:19:07");
        assertThat(stored.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT abs(extract(EPOCH FROM created_at - now())) < 60 "
                + "FROM mpesa_incoming_payments WHERE transaction_id = ?", Boolean.class, transId)).isTrue();

        TransactionDto row = feed.list(new TransactionFeedService.Filter(null, null, null, shortcode.getUuid(), null,
                transId, null, null), PageRequest.of(0, 5)).getContent().getFirst();
        assertThat(row.transactionDate().toInstant()).isEqualTo(Instant.parse("2026-06-02T14:19:07Z"));
        assertThat(row.createdAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void aLiveDepositFindsTheReceiptlessStkPaymentItSettles() {
        MpesaIncomingPayment stk = new MpesaIncomingPayment();
        String checkout = "ws_CO_" + UUID.randomUUID().toString().substring(0, 10);
        stk.setShortcodeUuid(shortcode.getUuid());
        stk.setPaymentType(IncomingPaymentType.STK_PUSH);
        stk.setCheckoutRequestId(checkout);
        stk.setTransactionId(checkout);
        stk.setPhoneNumber("254722000333");
        stk.setAmount(new BigDecimal("4500.00"));
        stk.setStatus(IncomingPaymentStatus.SUCCESS);
        UUID uuid = payments.saveAndFlush(stk).getUuid();
        jdbc.update("UPDATE mpesa_incoming_payments SET created_at = ? WHERE uuid = ?",
                Instant.now().minus(Duration.ofMinutes(2)).atOffset(ZoneOffset.UTC), uuid);
        String receipt = "TQ" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        C2BConfirmationService.Confirmation result = confirmations.confirm(
                confirmation(receipt, DarajaTime.compact(Instant.now()), "4500.00"), Map.of("TransID", receipt));

        assertThat(result.stkPayment()).isTrue();
        assertThat(result.payment().getUuid()).isEqualTo(uuid);
    }

    private C2BConfirmationCallback confirmation(String transId, String transTime, String amount) {
        return new C2BConfirmationCallback("Pay Bill", transId, transTime, new BigDecimal(amount),
                shortcode.getShortcode(), "ORDER-7731", "", null, null, "2547 ***** 333", "JANE", null, "KAMAU");
    }
}
