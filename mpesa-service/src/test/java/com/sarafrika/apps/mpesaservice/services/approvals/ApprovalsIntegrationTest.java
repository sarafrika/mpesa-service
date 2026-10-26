package com.sarafrika.apps.mpesaservice.services.approvals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.B2CPaymentResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.dtos.TransactionReversalResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.ApprovalDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.BulkPayoutUploadDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PayoutBatchDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.RequestReversalRequest;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.MakerCheckerException;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.ReversalRequest;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReversalRequestRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.approvals.bulk.BulkPayoutExecutor;
import com.sarafrika.apps.mpesaservice.services.approvals.bulk.BulkPayoutService;
import com.sarafrika.apps.mpesaservice.services.approvals.bulk.BulkPayoutViews;
import com.sarafrika.apps.mpesaservice.services.approvals.reversal.ReversalCallbackService;
import com.sarafrika.apps.mpesaservice.services.approvals.reversal.ReversalExecutor;
import com.sarafrika.apps.mpesaservice.services.approvals.reversal.ReversalService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.ReversalStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Maker-checker on real migrations: the maker cannot decide, two checkers cannot both approve, waiting
 * requests expire, and reversals and bulk payouts run once however often they are resumed. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ApprovalService.class, ApprovalRunner.class, ReversalService.class, ReversalExecutor.class,
        ReversalCallbackService.class, BulkPayoutService.class, BulkPayoutExecutor.class, BulkPayoutViews.class,
        MpesaPayoutService.class, AuditService.class, SecretCipher.class, ApprovalsIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApprovalsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    @EnableConfigurationProperties(ApprovalProperties.class)
    static class Beans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of());
        }

        /** Runs are started by the tests themselves, so each step can be checked. */
        @Bean(name = "approvalTaskExecutor")
        TaskExecutor approvalTaskExecutor() {
            return task -> {
            };
        }
    }

    @Autowired
    private ApprovalService approvals;
    @Autowired
    private ApprovalRunner runner;
    @Autowired
    private ReversalService reversalService;
    @Autowired
    private ReversalCallbackService reversalCallbacks;
    @Autowired
    private BulkPayoutService bulkPayouts;
    @Autowired
    private BulkPayoutViews batchViews;
    @Autowired
    private ReversalRequestRepository reversals;
    @Autowired
    private MpesaShortCodeRepository shortcodes;
    @Autowired
    private MpesaIncomingPaymentRepository payments;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private MpesaDarajaService daraja;

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
        shortcode = shortcodes.saveAndFlush(code);
        reset(daraja);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    // ---------- maker-checker ----------

    @Test
    void theMakerCannotApproveOrRejectTheirOwnRequest() {
        UUID request = reversalRequest(payment("15000"));

        as("finance-maker");
        assertThatThrownBy(() -> approvals.approve(request, null)).isInstanceOf(MakerCheckerException.class);
        assertThatThrownBy(() -> approvals.reject(request, "no")).isInstanceOf(MakerCheckerException.class);
        assertThat(approvals.get(request).status()).isEqualTo("WAITING");
        assertThat(approvals.get(request).ownRequest()).isTrue();
    }

    @Test
    void aRenamedMakerIsStillTheMaker() {
        UUID request = reversalRequest(payment("15000"));

        signIn("finance-maker-renamed", "sub-finance-maker", "ROLE_mpesa-finance");
        assertThatThrownBy(() -> approvals.approve(request, null)).isInstanceOf(MakerCheckerException.class);
        assertThat(approvals.get(request).ownRequest()).isTrue();
        assertThat(approvals.summary().waitingRaisedByYou()).isPositive();
    }

    @Test
    void aTokenWithoutASubjectCannotDecideOrRaise() {
        UUID request = reversalRequest(payment("15000"));

        signIn("checker", null, "ROLE_mpesa-finance", "ROLE_mpesa-operator");
        assertThatThrownBy(() -> approvals.approve(request, null)).isInstanceOf(MakerCheckerException.class)
                .hasMessageContaining("no subject");
        assertThatThrownBy(() -> reversalService.request(new RequestReversalRequest(payment("100").getUuid(), null,
                "Paid twice"))).isInstanceOf(MakerCheckerException.class);
        assertThat(approvals.get(request).status()).isEqualTo("WAITING");
    }

    @Test
    void aCheckerIsStoredWithTheirSubject() {
        UUID request = reversalRequest(payment("1500"));

        as("checker");
        approvals.approve(request, "ok");

        assertThat(jdbc.queryForObject("SELECT maker_sub || '/' || checker_sub FROM approval_requests WHERE uuid = ?",
                String.class, request)).isEqualTo("sub-finance-maker/sub-checker");
        assertThatThrownBy(() -> jdbc.update("UPDATE approval_requests SET checker_sub = maker_sub WHERE uuid = ?",
                request)).hasMessageContaining("chk_approval_checker_sub_not_maker");
    }

    @Test
    void theDatabaseRefusesAMakerAsChecker() {
        UUID request = reversalRequest(payment("100"));
        assertThatThrownBy(() -> jdbc.update("UPDATE approval_requests SET checker = maker WHERE uuid = ?", request))
                .hasMessageContaining("chk_approval_checker_not_maker");
    }

    @Test
    void twoCheckersApprovingAtOnceApproveItOnce() throws Exception {
        UUID request = reversalRequest(payment("2500"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> results = new ArrayList<>();
        for (String checker : List.of("checker-a", "checker-b")) {
            results.add(pool.submit(() -> {
                as(checker);
                start.await();
                try {
                    approvals.approve(request, "ok");
                    return "approved";
                } catch (ConflictException ex) {
                    return "conflict";
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get());
        }
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("approved", "conflict");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'APPROVAL_APPROVED' "
                + "AND entity_id = ?", Long.class, request.toString())).isEqualTo(1);
    }

    @Test
    void rejectingNeedsANoteAndCancelsTheReversal() {
        UUID request = reversalRequest(payment("300"));
        as("checker");
        assertThatThrownBy(() -> approvals.reject(request, " ")).isInstanceOf(InvalidRequestException.class)
                .extracting("field").isEqualTo("note");

        ApprovalDto rejected = approvals.reject(request, "Not a duplicate");

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.checker()).isEqualTo("checker");
        assertThat(rejected.decisionNote()).isEqualTo("Not a duplicate");
        assertThat(reversals.findByApprovalUuid(request).orElseThrow().getStatus()).isEqualTo(ReversalStatus.CANCELLED);
        assertThatThrownBy(() -> approvals.approve(request, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void waitingRequestsExpireAndCannotBeApprovedAfterwards() {
        MpesaIncomingPayment payment = payment("800");
        UUID request = reversalRequest(payment);
        jdbc.update("UPDATE approval_requests SET expires_at = NOW() - INTERVAL '1 minute' WHERE uuid = ?", request);

        assertThat(approvals.expireDue()).isEqualTo(1);

        assertThat(approvals.get(request).status()).isEqualTo("EXPIRED");
        assertThat(reversals.findByApprovalUuid(request).orElseThrow().getStatus()).isEqualTo(ReversalStatus.CANCELLED);
        as("checker");
        assertThatThrownBy(() -> approvals.approve(request, null)).isInstanceOf(ConflictException.class);
        // An expired reversal frees the payment for a new request.
        assertThat(reversalRequest(payment)).isNotEqualTo(request);
    }

    @Test
    void approvingPastTheExpiryExpiresItInstead() {
        UUID request = reversalRequest(payment("800"));
        jdbc.update("UPDATE approval_requests SET expires_at = NOW() - INTERVAL '1 second' WHERE uuid = ?", request);
        as("checker");
        assertThatThrownBy(() -> approvals.approve(request, null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("expired");
        assertThat(approvals.get(request).status()).isEqualTo("EXPIRED");
    }

    @Test
    void submitRefusesUnknownTypesAndBadAmounts() {
        as("someone");
        assertThatThrownBy(() -> approvals.submit(new ApprovalSubmission("TELEPORT", shortcode.getUuid(),
                BigDecimal.TEN, "why", null))).isInstanceOf(InvalidRequestException.class).extracting("field")
                .isEqualTo("type");
        assertThatThrownBy(() -> approvals.submit(new ApprovalSubmission(ApprovalTypes.REVERSAL, shortcode.getUuid(),
                BigDecimal.ZERO, "why", null))).isInstanceOf(InvalidRequestException.class).extracting("field")
                .isEqualTo("amount");
    }

    // ---------- reversal pre-checks ----------

    @Test
    void onlySettledPaymentsWithAReceiptAndAnAmountWithinTheOriginalCanBeReversed() {
        MpesaIncomingPayment pending = payment("500");
        pending.setStatus(IncomingPaymentStatus.PENDING);
        payments.saveAndFlush(pending);
        MpesaIncomingPayment noReceipt = payment("500");
        noReceipt.setTransactionId("");
        payments.saveAndFlush(noReceipt);
        MpesaIncomingPayment settled = payment("500");
        as("operator");

        assertThatThrownBy(() -> reversalService.request(new RequestReversalRequest(pending.getUuid(), null, "dup")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("successful");
        assertThatThrownBy(() -> reversalService.request(new RequestReversalRequest(noReceipt.getUuid(), null, "dup")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("receipt");
        assertThatThrownBy(() -> reversalService.request(new RequestReversalRequest(settled.getUuid(),
                new BigDecimal("501"), "dup"))).isInstanceOf(InvalidRequestException.class).extracting("field")
                .isEqualTo("amount");

        UUID first = reversalService.request(new RequestReversalRequest(settled.getUuid(), new BigDecimal("200"), "dup"));
        assertThatThrownBy(() -> reversalService.request(new RequestReversalRequest(settled.getUuid(), null, "again")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already has a reversal");

        ApprovalDto dto = approvals.get(first);
        assertThat(dto.title()).isEqualTo("Reverse " + settled.getTransactionId());
        assertThat(dto.amount()).isEqualByComparingTo("200");
        assertThat(dto.transactionUuid()).isEqualTo(settled.getUuid());
        assertThat(dto.darajaCall()).contains("/mpesa/reversal/v1/request");
        assertThat(dto.preChecks()).extracting("state").containsExactly("OK", "OK", "WARN");
        assertThat(dto.reversal().status()).isEqualTo("WAITING_APPROVAL");
    }

    // ---------- reversal execution ----------

    @Test
    void anApprovedReversalIsSentOnceAndSettledByItsResult() throws Exception {
        MpesaIncomingPayment payment = payment("15000");
        UUID request = reversalRequest(payment);
        when(daraja.reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString()))
                .thenReturn(MpesaApiResponse.success(new TransactionReversalResponse("AG_REV_1", "orig-1", "0",
                        "Accept the service request successfully."), 200));
        as("checker");
        approvals.approve(request, "Duplicate confirmed");

        runner.run(request);
        runner.run(request);

        verify(daraja, times(1)).reverseTransaction(any(MpesaShortCode.class), eq(payment.getTransactionId()),
                any(), anyString());
        ApprovalDto executed = approvals.get(request);
        assertThat(executed.status()).isEqualTo("EXECUTED");
        assertThat(executed.reversal().status()).isEqualTo("SENT");

        String result = """
                {"Result": {"ResultType": 0, "ResultCode": 0, "ResultDesc": "The service request is processed successfully.",
                  "OriginatorConversationID": "orig-1", "ConversationID": "AG_REV_1", "TransactionID": "SKE52PAWR9",
                  "ResultParameters": {"ResultParameter": [{"Key": "Amount", "Value": 15000.00},
                    {"Key": "OriginalTransactionID", "Value": "%s"}]}}}
                """.formatted(payment.getTransactionId());
        reversalCallbacks.applyResult(mapper.readTree(result), result);
        reversalCallbacks.applyResult(mapper.readTree(result), result);

        ReversalRequest reversal = reversals.findByApprovalUuid(request).orElseThrow();
        assertThat(reversal.getStatus()).isEqualTo(ReversalStatus.REVERSED);
        assertThat(reversal.getReversalTransactionId()).isEqualTo("SKE52PAWR9");
        assertThat(jdbc.queryForObject("SELECT reversal_transaction_id FROM mpesa_incoming_payments WHERE uuid = ?",
                String.class, payment.getUuid())).isEqualTo("SKE52PAWR9");
        assertThat(approvals.get(request).outcomeMessage()).contains("SKE52PAWR9");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'APPROVAL_EXECUTED' "
                + "AND entity_id = ?", Long.class, request.toString())).isEqualTo(1);

        as("operator");
        assertThatThrownBy(() -> reversalService.request(new RequestReversalRequest(payment.getUuid(), null, "x")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already been reversed");
    }

    @Test
    void aReversalSafaricomNeverAnsweredIsNotSentAgain() {
        UUID request = reversalRequest(payment("700"));
        when(daraja.reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString()))
                .thenReturn(MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "Read timed out", 503));
        as("checker");
        approvals.approve(request, null);

        runner.run(request);
        // A crash-resume of the same request finds the record and does not call Daraja again.
        jdbc.update("UPDATE approval_requests SET status = 'EXECUTING', heartbeat_at = NULL WHERE uuid = ?", request);
        runner.run(request);

        verify(daraja, times(1)).reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString());
        ApprovalDto dto = approvals.get(request);
        assertThat(dto.reversal().status()).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(dto.status()).isEqualTo("EXECUTED");
    }

    @Test
    void theResumeJobHandsRequestsToTheApprovalPoolInsteadOfRunningThem() {
        UUID request = reversalRequest(payment("640"));
        as("checker");
        approvals.approve(request, null);
        jdbc.update("UPDATE approval_requests SET decided_at = NOW() - INTERVAL '5 minutes' WHERE uuid = ?", request);

        assertThat(runner.resumeDue()).isPositive();

        // The test pool drops tasks, so nothing ran on this (scheduler) thread.
        verify(daraja, never()).reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString());
        assertThat(approvals.get(request).status()).isEqualTo("APPROVED");
    }

    @Test
    void financeClosesAStuckReversalAsFailedWhichFreesThePayment() throws Exception {
        MpesaIncomingPayment payment = payment("900");
        UUID request = stuckReversal(payment);
        ReversalRequest stuck = reversals.findByApprovalUuid(request).orElseThrow();

        // Daraja's R000002 refusal has no ResultParameters and names ids we never got: nothing matches it.
        String refusal = refusal("orig-lost-" + request, "AG_LOST_" + request);
        assertThat(reversalCallbacks.applyResult(mapper.readTree(refusal), refusal)).isEmpty();
        as("operator");
        assertThatThrownBy(() -> reversalService.request(new RequestReversalRequest(payment.getUuid(), null, "x")))
                .isInstanceOf(ConflictException.class);

        as("checker");
        assertThatThrownBy(() -> reversalCallbacks.closeAsFailed(stuck.getUuid(), " "))
                .isInstanceOf(InvalidRequestException.class);
        reversalCallbacks.closeAsFailed(stuck.getUuid(), "Statement shows the payment was not reversed");

        ReversalRequest closed = reversals.findByUuid(stuck.getUuid()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(ReversalStatus.FAILED);
        assertThat(closed.getClosedBy()).isEqualTo("checker");
        assertThat(closed.getCloseNote()).isEqualTo("Statement shows the payment was not reversed");
        assertThat(approvals.get(request).status()).isEqualTo("FAILED");
        assertThat(approvals.get(request).reversal().closedBy()).isEqualTo("checker");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'REVERSAL_CLOSED' "
                + "AND entity_id = ?", Long.class, stuck.getUuid().toString())).isEqualTo(1);
        assertThatThrownBy(() -> reversalCallbacks.closeAsFailed(stuck.getUuid(), "again"))
                .isInstanceOf(ConflictException.class);

        as("operator");
        assertThat(reversalService.request(new RequestReversalRequest(payment.getUuid(), null, "Paid twice")))
                .isNotNull();
        verify(daraja, times(1)).reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString());
    }

    @Test
    void anUnmatchedReversalResultIsAttachedToItsReversalByHand() throws Exception {
        MpesaIncomingPayment payment = payment("950");
        UUID request = stuckReversal(payment);
        ReversalRequest stuck = reversals.findByApprovalUuid(request).orElseThrow();

        String other = """
                {"Result":{"ResultType":0,"ResultCode":0,"ResultDesc":"ok","OriginatorConversationID":"o-x",
                  "ConversationID":"AG_X","TransactionID":"SKE0000001","ResultParameters":{"ResultParameter":[
                  {"Key":"OriginalTransactionID","Value":"NOTTHISONE"}]}}}""";
        assertThatThrownBy(() -> reversalCallbacks.attachResult(stuck.getUuid(), mapper.readTree(other), other))
                .isInstanceOf(ConflictException.class).hasMessageContaining("NOTTHISONE");

        String refusal = refusal("orig-att-" + request, "AG_ATT_" + request);
        reversalCallbacks.attachResult(stuck.getUuid(), mapper.readTree(refusal), refusal);

        ReversalRequest settled = reversals.findByUuid(stuck.getUuid()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(ReversalStatus.FAILED);
        assertThat(settled.getResultCode()).isEqualTo("R000002");
        assertThat(approvals.get(request).status()).isEqualTo("FAILED");
        verify(daraja, times(1)).reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString());
    }

    @Test
    void aRefusedReversalFailsTheRequestAndAnInterruptedSendBecomesUnknown() {
        UUID refused = reversalRequest(payment("700"));
        when(daraja.reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString()))
                .thenReturn(MpesaApiResponse.error("400.002.02", "Bad Request - Invalid TransactionID", 400));
        as("checker");
        approvals.approve(refused, null);
        runner.run(refused);
        assertThat(approvals.get(refused).status()).isEqualTo("FAILED");
        assertThat(approvals.get(refused).reversal().status()).isEqualTo("FAILED");

        UUID interrupted = reversalRequest(payment("700"));
        as("checker");
        approvals.approve(interrupted, null);
        jdbc.update("UPDATE reversal_requests SET status = 'SENDING' WHERE approval_uuid = ?", interrupted);
        jdbc.update("UPDATE approval_requests SET status = 'EXECUTING' WHERE uuid = ?", interrupted);
        reset(daraja);
        runner.run(interrupted);
        verify(daraja, never()).reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString());
        assertThat(approvals.get(interrupted).reversal().status()).isEqualTo("OUTCOME_UNKNOWN");
    }

    @Test
    void anAlreadyReversedResultMarksThePaymentReversed() throws Exception {
        MpesaIncomingPayment payment = payment("700");
        UUID request = reversalRequest(payment);
        when(daraja.reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString()))
                .thenReturn(MpesaApiResponse.success(new TransactionReversalResponse("AG_REV_2", "orig-2", "0",
                        "Accept the service request successfully."), 200));
        as("checker");
        approvals.approve(request, null);
        runner.run(request);
        String result = """
                {"Result": {"ResultType": 0, "ResultCode": "R000001", "ResultDesc": "The transaction has already been reversed",
                  "OriginatorConversationID": "orig-2", "ConversationID": "AG_REV_2", "TransactionID": "SKE0000000"}}
                """;
        reversalCallbacks.applyResult(mapper.readTree(result), result);

        assertThat(reversals.findByApprovalUuid(request).orElseThrow().getStatus()).isEqualTo(ReversalStatus.FAILED);
        assertThat(reversals.countReversedPayment(payment.getUuid())).isEqualTo(1);
        assertThat(approvals.get(request).status()).isEqualTo("FAILED");
    }

    // ---------- bulk payouts ----------

    @Test
    void anUploadSaysWhatIsWrongWithEachRow() {
        jdbc.update("UPDATE mpesa_shortcodes SET min_amount = 10, max_amount = 5000 WHERE uuid = ?", shortcode.getUuid());
        signIn("operator", "sub-operator", "ROLE_mpesa-operator");
        String csv = """
                phone,amount,reference,name
                0712345678,1500,OCT-1,Jane Wanjiru Kamau
                +254 110 123 456,200.00,OCT-2,
                12345,100,OCT-3,Bad Phone
                0712345679,abc,OCT-4,
                0712345679,1.50,OCT-5,
                0712345679,5,OCT-6,
                0712345679,6000,OCT-7,
                0712345679,100,OCT-1,
                0712345679,100,,
                """;

        BulkPayoutUploadDto preview = bulkPayouts.upload(upload(csv, true));

        assertThat(preview.preview()).isTrue();
        assertThat(preview.validCount()).isEqualTo(2);
        assertThat(preview.invalidCount()).isEqualTo(7);
        assertThat(preview.validTotal()).isEqualByComparingTo("1700");
        assertThat(preview.rows().get(1).phone()).isEqualTo("2541•••••456");
        assertThat(preview.rows().get(2).problems()).anyMatch(p -> p.contains("phone"));
        assertThat(preview.rows().get(3).problems()).contains("amount is not a number");
        assertThat(preview.rows().get(4).problems()).contains("amount must be whole shillings");
        assertThat(preview.rows().get(5).problems()).anyMatch(p -> p.contains("minimum"));
        assertThat(preview.rows().get(6).problems()).anyMatch(p -> p.contains("maximum"));
        assertThat(preview.rows().get(7).problems()).contains("reference repeats line 2");
        assertThat(preview.rows().get(8).problems()).contains("reference is missing");
        assertThat(preview.approvalUuid()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payout_batches", Long.class)).isZero();
    }

    @Test
    void anUploadRefusesFilesWithoutTheColumnsOrWithTooManyRows() {
        as("operator");
        assertThatThrownBy(() -> bulkPayouts.upload(upload("msisdn,value\n0712345678,10\n", true)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("amount");
        StringBuilder big = new StringBuilder("phone,amount,reference\n");
        for (int i = 0; i < 1001; i++) {
            big.append("0712345678,10,R-").append(i).append('\n');
        }
        assertThatThrownBy(() -> bulkPayouts.upload(upload(big.toString(), true)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("1000 rows");
    }

    @Test
    void aReferenceAnotherBatchClaimedMeanwhileRefusesTheWholeUploadUntilThatBatchCloses() {
        String ref = "C-" + UUID.randomUUID().toString().substring(0, 8);
        as("operator");
        BulkPayoutUploadDto first = bulkPayouts.upload(upload("phone,amount,reference\n0712345678,100," + ref
                + "-A\n", false));
        Long firstBatch = jdbc.queryForObject("SELECT id FROM payout_batches WHERE uuid = ?", Long.class,
                first.batchUuid());
        // As if another upload claimed ref-B between this upload's checks and its insert.
        jdbc.update("INSERT INTO payout_reference_claims (reference, batch_id) VALUES (?, ?)", ref + "-B", firstBatch);
        Long requestsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM approval_requests", Long.class);

        String second = "phone,amount,reference\n0712345679,200," + ref + "-B\n";
        assertThatThrownBy(() -> bulkPayouts.upload(upload(second, false))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("just queued");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_requests", Long.class)).isEqualTo(requestsBefore);

        as("checker");
        approvals.reject(first.approvalUuid(), "Wrong month");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payout_reference_claims WHERE batch_id = ?", Long.class,
                firstBatch)).isZero();
        as("operator");
        assertThat(bulkPayouts.upload(upload(second, false)).batchUuid()).isNotNull();
    }

    @Test
    void aRowWhoseReferenceAlreadyPaysAnotherPhoneIsRefusedNotMarkedSent() {
        String ref = "M-" + UUID.randomUUID().toString().substring(0, 8);
        as("operator");
        BulkPayoutUploadDto created = bulkPayouts.upload(upload("phone,amount,reference\n0712345678,100," + ref
                + "\n", false));
        // Another console payout took the reference after the batch was raised, to someone else.
        jdbc.update("""
                INSERT INTO mpesa_outgoing_payments (shortcode_uuid, payment_type, originator_conversation_id,
                  recipient_phone_number, amount, command_id, initiator_name, status, client_id, client_reference,
                  outcome_unknown, created_at)
                VALUES (?, 'B2C', ?, '254700000001', 100, 'BusinessPayment', 'elimika-api', 'PENDING',
                  'mpesa-console', ?, false, NOW() + INTERVAL '1 minute')""", shortcode.getUuid(), "oc-" + ref, ref);

        as("checker");
        approvals.approve(created.approvalUuid(), null);
        runner.run(created.approvalUuid());

        assertThat(jdbc.queryForObject("SELECT status FROM payout_batch_items WHERE reference = ?", String.class, ref))
                .isEqualTo("REFUSED");
        verify(daraja, never()).sendB2CPayout(any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void anApprovedBatchPaysEachRowOnceAndNeverResendsAnUnknownOne() {
        String ref = "B-" + UUID.randomUUID().toString().substring(0, 8);
        String csv = "phone,amount,reference\n0712345678,100," + ref + "-1\n0712345679,200," + ref
                + "-2\n0712345670,300," + ref + "-3\n";
        as("operator");
        BulkPayoutUploadDto created = bulkPayouts.upload(upload(csv, false));
        assertThat(created.batchUuid()).isNotNull();
        assertThat(approvals.get(created.approvalUuid()).amount()).isEqualByComparingTo("600");
        assertThat(approvals.get(created.approvalUuid()).batchUuid()).isEqualTo(created.batchUuid());

        // The same references cannot be queued again while the batch waits.
        BulkPayoutUploadDto again = bulkPayouts.upload(upload(csv, true));
        assertThat(again.validCount()).isZero();

        when(daraja.sendB2CPayout(any(), anyString(), eq("254712345678"), any(), any(), any()))
                .thenReturn(MpesaApiResponse.success(new B2CPaymentResponse("AG_1", "o", "0", "Accepted"), 200));
        when(daraja.sendB2CPayout(any(), anyString(), eq("254712345679"), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "Read timed out", 503));
        when(daraja.sendB2CPayout(any(), anyString(), eq("254712345670"), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error("400.002.02", "Bad Request - Invalid PartyB", 400));
        as("checker");
        approvals.approve(created.approvalUuid(), null);
        runner.run(created.approvalUuid());

        PayoutBatchDto batch = batchViews.batch(created.batchUuid());
        assertThat(batch.status()).isEqualTo("COMPLETED");
        assertThat(batch.processedCount()).isEqualTo(3);
        assertThat(batch.counts().pending()).isEqualTo(1);
        assertThat(batch.counts().unknown()).isEqualTo(1);
        assertThat(batch.counts().failed()).isEqualTo(1);
        assertThat(approvals.get(created.approvalUuid()).status()).isEqualTo("EXECUTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mpesa_outgoing_payments WHERE client_id = 'mpesa-console' "
                + "AND client_reference LIKE ?", Long.class, ref + "%")).isEqualTo(3);

        // Resuming the run (as after a crash) sends nothing again.
        jdbc.update("UPDATE approval_requests SET status = 'EXECUTING', heartbeat_at = NULL WHERE uuid = ?",
                created.approvalUuid());
        jdbc.update("UPDATE payout_batch_items SET status = 'QUEUED' WHERE reference = ?", ref + "-2");
        jdbc.update("UPDATE payout_batches SET status = 'RUNNING' WHERE uuid = ?", created.batchUuid());
        runner.run(created.approvalUuid());
        verify(daraja, times(3)).sendB2CPayout(any(), anyString(), anyString(), any(), any(), any());
        assertThat(batchViews.items(created.batchUuid(), "UNKNOWN",
                org.springframework.data.domain.PageRequest.of(0, 25)).getContent()).hasSize(1);

        // Paid this month: a new file with the same reference is refused.
        as("operator");
        assertThat(bulkPayouts.upload(upload(csv, true)).rows().get(0).problems())
                .anyMatch(p -> p.contains("already paid out"));
    }

    @Test
    void aRejectedBatchIsClosedAndItsReferencesFreed() {
        String ref = "R-" + UUID.randomUUID().toString().substring(0, 8);
        String csv = "phone,amount,reference\n0712345678,100," + ref + "\n";
        as("operator");
        BulkPayoutUploadDto created = bulkPayouts.upload(upload(csv, false));
        as("checker");
        approvals.reject(created.approvalUuid(), "Wrong month");
        assertThat(batchViews.batch(created.batchUuid()).status()).isEqualTo("REJECTED");
        as("operator");
        assertThat(bulkPayouts.upload(upload(csv, true)).validCount()).isEqualTo(1);
    }

    // ---------- helpers ----------

    private BulkPayoutService.Upload upload(String csv, boolean preview) {
        return new BulkPayoutService.Upload(csv.getBytes(StandardCharsets.UTF_8), "october.csv", shortcode.getUuid(),
                "October instructor payouts", "Earnings export for October", preview);
    }

    private UUID reversalRequest(MpesaIncomingPayment payment) {
        as("finance-maker");
        UUID uuid = reversalService.request(new RequestReversalRequest(payment.getUuid(), null, "Paid twice"));
        SecurityContextHolder.clearContext();
        return uuid;
    }

    /** A reversal sent to a Safaricom that never answered, so its outcome is unknown and it holds the payment. */
    private UUID stuckReversal(MpesaIncomingPayment payment) {
        UUID request = reversalRequest(payment);
        when(daraja.reverseTransaction(any(MpesaShortCode.class), anyString(), any(), anyString()))
                .thenReturn(MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "Read timed out", 503));
        as("checker");
        approvals.approve(request, null);
        runner.run(request);
        assertThat(reversals.findByApprovalUuid(request).orElseThrow().getStatus())
                .isEqualTo(ReversalStatus.OUTCOME_UNKNOWN);
        return request;
    }

    /** Daraja's reversal refusal sample (developer.safaricom.co.ke/apis/Reversal): no ResultParameters. */
    private static String refusal(String originator, String conversation) {
        return """
                {"Result":{"ResultType":0,"ResultCode":"R000002","ResultDesc":"The OriginalTransID is invalid.",
                  "OriginatorConversationID":"%s","ConversationID":"%s","TransactionID":"OHO11HK9IO",
                  "ReferenceData":{"ReferenceItem":{"Key":"QueueTimeoutURL",
                  "Value":"https://internalsandbox.safaricom.co.ke/mpesa/reversalresults/v1/submit"}}}}"""
                .formatted(originator, conversation);
    }

    private MpesaIncomingPayment payment(String amount) {
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setShortcodeUuid(shortcode.getUuid());
        payment.setPaymentType(IncomingPaymentType.C2B);
        payment.setTransactionId("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase());
        payment.setPhoneNumber("254712345678");
        payment.setAmount(new BigDecimal(amount));
        payment.setAccountReference("ELM-004512");
        payment.setStatus(IncomingPaymentStatus.SUCCESS);
        return payments.saveAndFlush(payment);
    }

    private static void as(String user) {
        signIn(user, "sub-" + user, "ROLE_mpesa-finance", "ROLE_mpesa-operator");
    }

    /** A Keycloak-like token; a null sub leaves the subject out. */
    private static void signIn(String username, String sub, String... roles) {
        Jwt.Builder token = Jwt.withTokenValue("t").header("alg", "none").claim("preferred_username", username);
        if (sub != null) {
            token.subject(sub);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token.build(),
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }
}
