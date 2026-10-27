package com.sarafrika.apps.mpesaservice.services.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.AccountBalanceResponse;
import com.sarafrika.apps.mpesaservice.dtos.AccountBalanceResultData;
import com.sarafrika.apps.mpesaservice.dtos.AccountBalanceResultParameter;
import com.sarafrika.apps.mpesaservice.dtos.AccountBalanceResultParameters;
import com.sarafrika.apps.mpesaservice.dtos.B2BPaymentRequest;
import com.sarafrika.apps.mpesaservice.dtos.B2BResult;
import com.sarafrika.apps.mpesaservice.dtos.B2BTransferResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.dtos.PayoutFloatDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bDestinationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bSweepSubmittedDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.BalanceSnapshotDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.CreateB2bDestinationRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.RequestB2bSweepRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.ShortcodeFloatStatusDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.UpdateB2bDestinationRequest;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.B2bDestination;
import com.sarafrika.apps.mpesaservice.models.B2bTransfer;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.B2bTransferRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalExecutor;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalGateway;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalSubmission;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsConfig;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bReceiverType;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceSnapshotStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** Balance queries, the scheduled claim, thresholds and B2B sweeps on real migrations (Hibernate validates). */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "mpesa.treasury.balance-query-interval=30m"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BalanceService.class, BalanceQueryClaims.class, BalanceQueryScheduler.class, B2bDestinationService.class,
        B2bSweepService.class, B2bTransferService.class, B2bSweepExecutor.class, TreasuryConfig.class,
        MetricsConfig.class, AuditService.class, SecretCipher.class, TreasuryIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TreasuryIntegrationTest {

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
    private BalanceService balances;
    @Autowired
    private BalanceQueryClaims claims;
    @Autowired
    private BalanceQueryScheduler scheduler;
    @Autowired
    private B2bDestinationService destinations;
    @Autowired
    private B2bSweepService sweeps;
    @Autowired
    private B2bTransferService transfers;
    @Autowired
    private B2bSweepExecutor executor;
    @Autowired
    private B2bTransferRepository transferRepository;
    @Autowired
    private MpesaShortCodeRepository shortCodeRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MpesaDarajaService daraja;
    @MockitoBean
    private MpesaPayoutService payouts;
    @MockitoBean
    private ApprovalGateway gateway;

    private MpesaShortCode shortcode;
    private int sequence;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM b2b_transfers");
        jdbc.update("DELETE FROM b2b_destinations");
        jdbc.update("DELETE FROM balance_snapshots");
        jdbc.update("DELETE FROM mpesa_shortcodes");
        shortcode = shortcode(true, "elimika-api");
        when(payouts.latestFloat(any())).thenAnswer(call -> new PayoutFloatDto(call.getArgument(0), null, null, null));
    }

    // ---- Account Balance

    @Test
    void aBalanceQueryWaitsForItsResultAndARepeatedResultChangesNothing() throws Exception {
        acceptBalanceQuery("AG_B1", "OC-B1");

        BalanceSnapshotDto pending = balances.requestQuery(shortcode.getUuid());

        assertThat(pending.status()).isEqualTo(BalanceSnapshotStatus.PENDING);
        assertThat(balances.status(shortcode.getUuid()).lastQuery().status()).isEqualTo(BalanceSnapshotStatus.PENDING);
        assertThat(balances.status(shortcode.getUuid()).latest()).isNull();

        balances.applyResult(balanceResult("OC-B1", "AG_B1", AccountBalanceParserTest.DARAJA_SAMPLE), "{\"a\":1}");
        balances.applyResult(balanceResult("OC-B1", "AG_B1",
                "Utility Account|KES|1.00|1.00|0.00|0.00"), "{\"a\":2}");

        ShortcodeFloatStatusDto status = balances.status(shortcode.getUuid());
        assertThat(status.latest().status()).isEqualTo(BalanceSnapshotStatus.COMPLETED);
        assertThat(status.latest().utilityAvailable()).isEqualByComparingTo("228037.00");
        assertThat(status.latest().workingAvailable()).isEqualByComparingTo("700000.00");
        assertThat(status.latest().chargesPaidAvailable()).isEqualByComparingTo("-1540.00");
        assertThat(status.latest().accounts()).hasSize(5);
        assertThat(status.utilitySource()).isEqualTo(KnownFloat.BALANCE_QUERY);
        assertThat(status.lowFloat()).isFalse();
        assertThat(jdbc.queryForObject("SELECT raw->>'a' FROM balance_snapshots", String.class)).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'BALANCE_QUERIED' AND entity_id = ?",
                Integer.class, shortcode.getUuid().toString())).isEqualTo(1);
        assertThat(balances.history(shortcode.getUuid(), null, null)).hasSize(1);
    }

    @Test
    void aRefusedQueryIsA502AndAnUnreachableSafaricomA503() {
        when(daraja.queryAccountBalance(any(), any())).thenReturn(
                MpesaApiResponse.error("400.002.02", "Bad Request - Invalid PartyA", 400));
        assertThatThrownBy(() -> balances.requestQuery(shortcode.getUuid()))
                .isInstanceOfSatisfying(UpstreamException.class, ex -> assertThat(ex.isUnavailable()).isFalse())
                .hasMessageContaining("Invalid PartyA");

        when(daraja.queryAccountBalance(any(), any())).thenReturn(
                MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "timeout", 503));
        assertThatThrownBy(() -> balances.requestQuery(shortcode.getUuid()))
                .isInstanceOfSatisfying(UpstreamException.class, ex -> assertThat(ex.isUnavailable()).isTrue());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM balance_snapshots WHERE status = 'FAILED'",
                Integer.class)).isEqualTo(1);
        // No answer is not a refusal: the query stays PENDING with its outcome unknown.
        assertThat(jdbc.queryForObject("SELECT result_desc FROM balance_snapshots WHERE status = 'PENDING'",
                String.class)).isEqualTo(BalanceService.UNKNOWN_OUTCOME);
    }

    @Test
    void aQueryNeedsAnInitiator() {
        MpesaShortCode bare = shortcode(true, null);
        assertThatThrownBy(() -> balances.requestQuery(bare.getUuid())).isInstanceOf(ConflictException.class);
        verify(daraja, never()).queryAccountBalance(any(), any());
    }

    @Test
    void aTimedOutQueryCanStillBeCompletedByALateResult() {
        acceptBalanceQuery("AG_T1", "OC-T1");
        balances.requestQuery(shortcode.getUuid());

        balances.applyTimeout("OC-T1", null, "The service request timed out.");
        assertThat(balances.status(shortcode.getUuid()).lastQuery().status()).isEqualTo(BalanceSnapshotStatus.TIMED_OUT);

        balances.applyResult(balanceResult(null, "AG_T1", AccountBalanceParserTest.DARAJA_SAMPLE), "{}");
        assertThat(balances.status(shortcode.getUuid()).lastQuery().status()).isEqualTo(BalanceSnapshotStatus.COMPLETED);
    }

    @Test
    void theThresholdFallsBackToTheDefaultAndIsAudited() throws Exception {
        acceptBalanceQuery("AG_H1", "OC-H1");
        balances.requestQuery(shortcode.getUuid());
        balances.applyResult(balanceResult("OC-H1", "AG_H1", AccountBalanceParserTest.DARAJA_SAMPLE), "{}");

        ShortcodeFloatStatusDto own = balances.updateThreshold(shortcode.getUuid(), new BigDecimal("300000"));
        assertThat(own.lowFloatThreshold()).isEqualByComparingTo("300000");
        assertThat(own.lowFloat()).isTrue();

        ShortcodeFloatStatusDto fallback = balances.updateThreshold(shortcode.getUuid(), null);
        assertThat(fallback.lowFloatThresholdOverride()).isNull();
        assertThat(fallback.lowFloatThreshold()).isEqualByComparingTo("50000");
        assertThat(fallback.lowFloat()).isFalse();
        assertThat(fallback.autoQueryIntervalMinutes()).isEqualTo(30L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'FLOAT_THRESHOLD_UPDATED' AND entity_id = ?",
                Integer.class, shortcode.getUuid().toString())).isEqualTo(2);
    }

    // ---- scheduled query

    @Test
    void theScheduleClaimsEachDueShortcodeOnceHoweverManyInstancesAsk() throws Exception {
        MpesaShortCode second = shortcode(true, "api-2");
        shortcode(true, null);
        shortcode(false, "api-3");
        LocalDateTime now = LocalDateTime.now();

        Set<UUID> claimed = ConcurrentHashMap.newKeySet();
        int total;
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<UUID>>> runs = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                runs.add(pool.submit(() -> {
                    start.await();
                    return claims.claimDue(now, now.plusMinutes(30), 10);
                }));
            }
            start.countDown();
            total = 0;
            for (Future<List<UUID>> run : runs) {
                List<UUID> got = run.get();
                total += got.size();
                claimed.addAll(got);
            }
        }

        assertThat(total).isEqualTo(2);
        assertThat(claimed).containsExactlyInAnyOrder(shortcode.getUuid(), second.getUuid());
        assertThat(claims.claimDue(now.plusMinutes(29), now.plusMinutes(59), 10)).isEmpty();
        assertThat(claims.claimDue(now.plusMinutes(31), now.plusMinutes(61), 10)).hasSize(2);
    }

    @Test
    void theScheduledPassQueriesEveryClaimedShortcode() {
        acceptBalanceQuery("AG_S", "OC-S");
        shortcode(true, "api-2");

        assertThat(scheduler.runOnce()).isEqualTo(2);
        assertThat(scheduler.runOnce()).isZero();
        verify(daraja, times(2)).queryAccountBalance(any(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM balance_snapshots WHERE source = 'SCHEDULED'",
                Integer.class)).isEqualTo(2);
    }

    // ---- destinations and sweeps

    @Test
    void destinationsNeedAnAccountForAPaybillAndAUniqueName() {
        assertThatThrownBy(() -> destinations.create(shortcode.getUuid(), new CreateB2bDestinationRequest(
                "Bank", "522522", B2bReceiverType.PAYBILL, null, null)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("account_reference"));
        B2bDestinationDto till = destinations.create(shortcode.getUuid(), new CreateB2bDestinationRequest(
                "Supplier till", "123456", B2bReceiverType.TILL, null, null));
        assertThat(till.commandId()).isEqualTo("BusinessBuyGoods");
        assertThatThrownBy(() -> destinations.create(shortcode.getUuid(), new CreateB2bDestinationRequest(
                "supplier TILL", "654321", B2bReceiverType.TILL, null, null))).isInstanceOf(ConflictException.class);
        assertThat(destinations.list(shortcode.getUuid(), true, PageRequest.of(0, 25)).getTotalElements()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'B2B_DESTINATION_CREATED' AND entity_id = ?",
                Integer.class, till.uuid().toString())).isEqualTo(1);
    }

    @Test
    void aSweepIsCheckedThenSubmittedToApprovals() throws Exception {
        B2bDestinationDto bank = bank(shortcode);
        MpesaShortCode other = shortcode(true, "api-x");
        B2bDestinationDto foreign = bank(other);
        when(payouts.latestFloat(shortcode.getUuid())).thenReturn(new PayoutFloatDto(shortcode.getUuid(),
                new BigDecimal("600000.00"), null, LocalDateTime.now()));
        UUID approval = UUID.randomUUID();
        when(gateway.submit(any())).thenReturn(approval);

        assertThatThrownBy(() -> sweeps.request(shortcode.getUuid(), sweep(bank.uuid(), "600001")))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> assertThat(ex.getField()).isEqualTo("amount"));
        assertThatThrownBy(() -> sweeps.request(shortcode.getUuid(), sweep(bank.uuid(), "100.50")))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> assertThat(ex.getField()).isEqualTo("amount"));
        assertThatThrownBy(() -> sweeps.request(shortcode.getUuid(), sweep(foreign.uuid(), "100")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("destination_uuid"));
        destinations.update(shortcode.getUuid(), bank.uuid(), new UpdateB2bDestinationRequest(null, null, null, null,
                false));
        assertThatThrownBy(() -> sweeps.request(shortcode.getUuid(), sweep(bank.uuid(), "100")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("destination_uuid"));
        destinations.update(shortcode.getUuid(), bank.uuid(), new UpdateB2bDestinationRequest(null, null, null, null,
                true));

        B2bSweepSubmittedDto submitted = sweeps.request(shortcode.getUuid(), sweep(bank.uuid(), "500000"));

        assertThat(submitted.approvalRequestUuid()).isEqualTo(approval);
        ArgumentCaptor<ApprovalSubmission> captor = ArgumentCaptor.forClass(ApprovalSubmission.class);
        verify(gateway).submit(captor.capture());
        ApprovalSubmission submission = captor.getValue();
        assertThat(submission.type()).isEqualTo("B2B_SWEEP");
        assertThat(submission.amount()).isEqualByComparingTo("500000");
        assertThat(submission.reason()).isEqualTo("Weekly sweep");
        JsonNode payload = submission.payload();
        assertThat(payload.get("command_id").asText()).isEqualTo("BusinessPayBill");
        assertThat(payload.get("receiver_shortcode").asText()).isEqualTo("522522");
        assertThat(payload.get("account_reference").asText()).isEqualTo("SAR-001");
        assertThat(payload.get("balance_after").asText()).isEqualTo("100000.00");
        assertThat(payload.get("reference").asText()).isEqualTo(submitted.reference()).startsWith("SWP-");
        verify(daraja, never()).sendB2BPayment(any(), any());
    }

    @Test
    void anApprovedSweepIsSentOnceAndSettledOnceByItsResult() throws Exception {
        B2bDestinationDto bank = bank(shortcode);
        when(daraja.sendB2BPayment(any(), any())).thenReturn(MpesaApiResponse.success(
                new B2BTransferResponse("AG_X1", "OC-X1", "0", "Accept the service request successfully."), 200));
        ApprovalExecutor.Approved approved = approved(bank, "250000");

        assertThat(executor.type()).isEqualTo("B2B_SWEEP");
        ApprovalExecutor.Outcome first = executor.execute(approved);
        ApprovalExecutor.Outcome again = executor.execute(approved);

        assertThat(first.sent()).isTrue();
        assertThat(again.sent()).isTrue();
        assertThat(again.message()).startsWith("Already sent");
        ArgumentCaptor<B2BPaymentRequest> sent = ArgumentCaptor.forClass(B2BPaymentRequest.class);
        verify(daraja, times(1)).sendB2BPayment(any(), sent.capture());
        assertThat(sent.getValue().commandId()).isEqualTo("BusinessPayBill");
        assertThat(sent.getValue().accountReference()).isEqualTo("SAR-001");

        transfers.applyResult(new B2BResult(0, 0, "The service request is processed successfully.", "OC-X1", "AG_X1",
                "QKA81LK5CY", null, null), "{}");
        transfers.applyResult(new B2BResult(0, 2001, "The initiator information is invalid.", "OC-X1", "AG_X1",
                "OAK0000000", null, null), "{}");

        B2bTransfer transfer = transferRepository.findByApprovalRequestUuid(approved.requestUuid()).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(B2bTransferStatus.SUCCESS);
        assertThat(transfer.getTransactionId()).isEqualTo("QKA81LK5CY");
        assertThat(transfer.getMaker()).isEqualTo("maker");
        assertThat(transfer.getChecker()).isEqualTo("checker");
        assertThat(sweeps.list(shortcode.getUuid(), null, PageRequest.of(0, 25)).getContent().getFirst()
                .destinationName()).isEqualTo("Sarafrika bank");
    }

    @Test
    void aTimeoutLeavesTheSweepPendingWithItsOutcomeUnknown() {
        B2bDestinationDto bank = bank(shortcode);
        when(daraja.sendB2BPayment(any(), any())).thenReturn(MpesaApiResponse.success(
                new B2BTransferResponse("AG_X2", "OC-X2", "0", "Accept"), 200));
        ApprovalExecutor.Approved approved = approved(bank, "1000");
        executor.execute(approved);

        transfers.applyTimeout("OC-X2", "AG_X2", "The service request timed out.");

        B2bTransfer transfer = transferRepository.findByApprovalRequestUuid(approved.requestUuid()).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(B2bTransferStatus.PENDING);
        assertThat(transfer.isOutcomeUnknown()).isTrue();
        assertThat(transfer.getTimedOutAt()).isNotNull();
        assertThat(executor.execute(approved).message()).startsWith("Already sent");
        verify(daraja, times(1)).sendB2BPayment(any(), any());
    }

    @Test
    void aRefusalFailsTheSweepAndNoAnswerFlagsIt() {
        B2bDestinationDto bank = bank(shortcode);
        when(daraja.sendB2BPayment(any(), any())).thenReturn(
                MpesaApiResponse.error("400.002.02", "Bad Request - Invalid Amount", 400));
        ApprovalExecutor.Outcome refused = executor.execute(approved(bank, "1000"));
        assertThat(refused.sent()).isFalse();
        assertThat(refused.message()).contains("Invalid Amount");

        when(daraja.sendB2BPayment(any(), any())).thenReturn(
                MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "read timed out", 503));
        ApprovalExecutor.Approved unanswered = approved(bank, "1000");
        ApprovalExecutor.Outcome unknown = executor.execute(unanswered);
        assertThat(unknown.sent()).isTrue();
        B2bTransfer transfer = transferRepository.findByApprovalRequestUuid(unanswered.requestUuid()).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(B2bTransferStatus.PENDING);
        assertThat(transfer.isOutcomeUnknown()).isTrue();
    }

    @Test
    void aSweepWithoutADarajaTokenIsOurFailureNotSafaricomsRefusal() {
        B2bDestinationDto bank = bank(shortcode);
        when(daraja.sendB2BPayment(any(), any())).thenReturn(
                MpesaApiResponse.error("AUTH_FAILED", "Failed to obtain access token", 401));
        ApprovalExecutor.Approved approved = approved(bank, "1000");

        ApprovalExecutor.Outcome outcome = executor.execute(approved);

        assertThat(outcome.sent()).isFalse();
        assertThat(outcome.message()).startsWith("Not sent: We could not authenticate with Daraja")
                .contains("Check the credentials").doesNotContain("Safaricom refused");
        B2bTransfer transfer = transferRepository.findByApprovalRequestUuid(approved.requestUuid()).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(B2bTransferStatus.FAILED);
        assertThat(transfer.isOutcomeUnknown()).isFalse();
    }

    @Test
    void aResultForASweepSafaricomNeverAcknowledgedMatchesTheOneOpenTransferThatFits() throws Exception {
        B2bDestinationDto bank = bank(shortcode);
        when(daraja.sendB2BPayment(any(), any())).thenReturn(
                MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "read timed out", 503));
        ApprovalExecutor.Approved unanswered = approved(bank, "7300");
        executor.execute(unanswered);

        B2BResult result = b2bResult("OC-LOST-1", "AG_LOST_1", 0, "7300.00", "522522 - Sarafrika Bank", "SAR-001");
        assertThat(transfers.applyResult(result, "{}")).isPresent();

        B2bTransfer transfer = transferRepository.findByApprovalRequestUuid(unanswered.requestUuid()).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(B2bTransferStatus.SUCCESS);
        assertThat(transfer.isOutcomeUnknown()).isFalse();
        assertThat(transfer.getConversationId()).isEqualTo("AG_LOST_1");
        verify(daraja, times(1)).sendB2BPayment(any(), any());
    }

    @Test
    void aResultThatFitsTwoOpenTransfersMatchesNeitherAndCanBeAttachedOrTheTransferClosed() throws Exception {
        B2bDestinationDto bank = bank(shortcode);
        when(daraja.sendB2BPayment(any(), any())).thenReturn(
                MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "read timed out", 503));
        ApprovalExecutor.Approved first = approved(bank, "8800");
        ApprovalExecutor.Approved second = approved(bank, "8800");
        executor.execute(first);
        executor.execute(second);
        UUID firstUuid = transferRepository.findByApprovalRequestUuid(first.requestUuid()).orElseThrow().getUuid();
        UUID secondUuid = transferRepository.findByApprovalRequestUuid(second.requestUuid()).orElseThrow().getUuid();

        B2BResult result = b2bResult("OC-LOST-2", "AG_LOST_2", 0, "8800", "522522 - Sarafrika Bank", null);
        assertThat(transfers.applyResult(result, "{}")).isEmpty();
        // A refusal carries no amount or receiver, so it never matches by them either.
        assertThat(transfers.applyResult(b2bResult("OC-LOST-3", "AG_LOST_3", 2001, null, null, null), "{}"))
                .isEmpty();

        assertThatThrownBy(() -> transfers.attachResult(firstUuid,
                b2bResult("OC-LOST-2", "AG_LOST_2", 0, "9900", "522522 - Sarafrika Bank", null), "{}"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("9900");
        B2bTransfer attached = transfers.attachResult(firstUuid, result, "{\"Result\":{}}");
        assertThat(attached.getStatus()).isEqualTo(B2bTransferStatus.SUCCESS);
        assertThatThrownBy(() -> transfers.attachResult(firstUuid, result, "{}")).isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> transfers.closeAsFailed(shortcode.getUuid(), secondUuid, " "))
                .isInstanceOf(InvalidRequestException.class);
        B2bTransfer closed = transfers.closeAsFailed(shortcode.getUuid(), secondUuid, "Statement shows no debit");
        assertThat(closed.getStatus()).isEqualTo(B2bTransferStatus.FAILED);
        assertThat(closed.getClosedBy()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'B2B_TRANSFER_CLOSED' "
                + "AND entity_id = ?", Integer.class, secondUuid.toString())).isEqualTo(1);
        assertThatThrownBy(() -> transfers.closeAsFailed(shortcode.getUuid(), firstUuid, "again"))
                .isInstanceOf(ConflictException.class);
        verify(daraja, times(2)).sendB2BPayment(any(), any());
    }

    @Test
    void aSweepSubmissionCarriesItsTitleDarajaCallAndPreChecks() {
        B2bDestinationDto bank = bank(shortcode);
        when(payouts.latestFloat(shortcode.getUuid())).thenReturn(new PayoutFloatDto(shortcode.getUuid(),
                new BigDecimal("600000.00"), null, LocalDateTime.now()));
        when(gateway.submit(any())).thenReturn(UUID.randomUUID());

        sweeps.request(shortcode.getUuid(), sweep(bank.uuid(), "590000"));

        ArgumentCaptor<ApprovalSubmission> captor = ArgumentCaptor.forClass(ApprovalSubmission.class);
        verify(gateway).submit(captor.capture());
        JsonNode payload = captor.getValue().payload();
        assertThat(payload.get("title").asText()).isEqualTo("Sweep KES 590,000.00 to Sarafrika bank");
        assertThat(payload.get("daraja_call").asText()).isEqualTo("POST /mpesa/b2b/v1/paymentrequest · BusinessPayBill");
        assertThat(payload.get("pre_checks")).hasSize(3);
        assertThat(payload.get("pre_checks").get(2).get("state").asText()).isEqualTo("WARN");
    }

    @Test
    void aDestinationChangedAfterTheRequestReceivesNothing() {
        B2bDestinationDto bank = bank(shortcode);
        ApprovalExecutor.Approved approved = approved(bank, "1000");
        destinations.update(shortcode.getUuid(), bank.uuid(), new UpdateB2bDestinationRequest(null, "999999", null,
                null, null));

        ApprovalExecutor.Outcome outcome = executor.execute(approved);

        assertThat(outcome.sent()).isFalse();
        assertThat(outcome.message()).contains("changed");
        verify(daraja, never()).sendB2BPayment(any(), any());
    }

    // ---- helpers

    private MpesaShortCode shortcode(boolean active, String initiator) {
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(String.valueOf(600000 + (++sequence)));
        code.setShortcodeType(ShortcodeType.PAYBILL);
        code.setBusinessName("Elimika Fees " + sequence);
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setCallbackUrl("https://example.test/api/v1/callbacks/stk/callback");
        code.setEnvironment(Environment.SANDBOX);
        code.setInitiatorName(initiator);
        code.setIsActive(active);
        return shortCodeRepository.saveAndFlush(code);
    }

    private B2bDestinationDto bank(MpesaShortCode owner) {
        return destinations.create(owner.getUuid(), new CreateB2bDestinationRequest("Sarafrika bank", "522522",
                B2bReceiverType.PAYBILL, "SAR-001", true));
    }

    private ApprovalExecutor.Approved approved(B2bDestinationDto destination, String amount) {
        MpesaShortCode owner = shortCodeRepository.findByUuid(destination.shortcodeUuid()).orElseThrow();
        B2bDestination entity = new B2bDestination();
        entity.setUuid(destination.uuid());
        entity.setShortcodeUuid(destination.shortcodeUuid());
        entity.setName(destination.name());
        entity.setReceiverShortcode(destination.receiverShortcode());
        entity.setReceiverType(destination.receiverType());
        entity.setAccountReference(destination.accountReference());
        JsonNode payload = sweeps.payload(owner, entity, new BigDecimal(amount), B2bSweepService.reference(),
                KnownFloat.UNKNOWN);
        return new ApprovalExecutor.Approved(UUID.randomUUID(), "B2B_SWEEP", owner.getUuid(), new BigDecimal(amount),
                payload, "maker", "checker");
    }

    /** A B2B result shaped like Daraja's BusinessPayBill sample; null amount and party leave the parameters out. */
    private B2BResult b2bResult(String originator, String conversation, int code, String amount, String party,
                                String billReference) throws Exception {
        String params = amount == null ? "" : """
                ,"ResultParameters":{"ResultParameter":[{"Key":"Amount","Value":"%s"},
                  {"Key":"ReceiverPartyPublicName","Value":"%s"},{"Key":"Currency","Value":"KES"}]}"""
                .formatted(amount, party);
        String reference = billReference == null ? "" : """
                ,"ReferenceData":{"ReferenceItem":[{"Key":"BillReferenceNumber","Value":"%s"}]}"""
                .formatted(billReference);
        String body = """
                {"Result":{"ResultType":0,"ResultCode":%d,"ResultDesc":"done","OriginatorConversationID":"%s",
                 "ConversationID":"%s","TransactionID":"QKA81LK5CY"%s%s}}"""
                .formatted(code, originator, conversation, params, reference);
        return objectMapper.readValue(body, com.sarafrika.apps.mpesaservice.dtos.B2BTransferResult.class).result();
    }

    private static RequestB2bSweepRequest sweep(UUID destination, String amount) {
        return new RequestB2bSweepRequest(destination, new BigDecimal(amount), "Weekly sweep");
    }

    private void acceptBalanceQuery(String conversationId, String originatorId) {
        when(daraja.queryAccountBalance(any(), any())).thenReturn(MpesaApiResponse.success(
                new AccountBalanceResponse(conversationId, originatorId, "0", "Accept the service request successfully."),
                200));
    }

    private static AccountBalanceResultData balanceResult(String originatorId, String conversationId, String balance) {
        return new AccountBalanceResultData(0, 0, "The service request is processed successfully.", originatorId,
                conversationId, new AccountBalanceResultParameters(List.of(
                        new AccountBalanceResultParameter("AccountBalance", balance),
                        new AccountBalanceResultParameter("BOCompletedTime", "20200109125710"))));
    }
}
