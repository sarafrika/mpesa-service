package com.sarafrika.apps.mpesaservice.services.daraja.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.B2bCheckoutDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.ProductPayoutDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.RequestPochiPayoutRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.RequestTaxRemittanceRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.StartB2bCheckoutRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.features.ShortcodeFeatureDto;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.ProductPayout;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.ProductPayoutRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalExecutor;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalGateway;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalSubmission;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.features.FeatureAdminService;
import com.sarafrika.apps.mpesaservice.services.features.FeatureGate;
import com.sarafrika.apps.mpesaservice.services.features.ShortcodeFeatures;
import com.sarafrika.apps.mpesaservice.services.payout.SecurityCredentialService;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bCheckoutStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ProductPayoutStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Feature switches, Tax Remittance, Business to Pochi and B2B Express Checkout on real migrations. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "mpesa.callback.base-url=https://pay.example.test/api/v1/callbacks"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductPayoutService.class, B2bCheckoutService.class, DarajaProductEndpoints.class, FeatureGate.class,
        ShortcodeFeatures.class, FeatureAdminService.class, TaxRemittanceExecutor.class, PochiPayoutExecutor.class,
        AuditService.class, SecretCipher.class, DarajaProductsIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RecordApplicationEvents
class DarajaProductsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    // The success sample on developer.safaricom.co.ke/apis/TaxRemittance (ResultParameter trimmed).
    private static final String TAX_SUCCESS = """
            {"Result":{"ResultType":"0","ResultCode":"0","ResultDesc":"The service request is processed successfully",
            "OriginatorConversationID":"%s","ConversationID":"AG_20181005_00004d7ee675c0c7ee0b",
            "TransactionID":"QKA81LK5CY","ResultParameters":{"ResultParameter":[{"Key":"Amount","Value":"190.00"},
            {"Key":"ReceiverPartyPublicName","Value":"00000 - Tax Collecting Company"}]},
            "ReferenceData":{"ReferenceItem":{"Key":"BillReferenceNumber","Value":"19008"}}}}""";

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
    private ProductPayoutService payouts;
    @Autowired
    private B2bCheckoutService checkouts;
    @Autowired
    private TaxRemittanceExecutor taxExecutor;
    @Autowired
    private PochiPayoutExecutor pochiExecutor;
    @Autowired
    private FeatureAdminService featureAdmin;
    @Autowired
    private ShortcodeFeatures features;
    @Autowired
    private ProductPayoutRepository payoutRepository;
    @Autowired
    private MpesaShortCodeRepository shortCodeRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private MpesaDarajaService daraja;
    @MockitoBean
    private SecurityCredentialService credentials;
    @MockitoBean
    private ApprovalGateway gateway;

    private MpesaShortCode shortcode;
    private int sequence;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM product_payouts");
        jdbc.update("DELETE FROM b2b_checkout_requests");
        jdbc.update("DELETE FROM mpesa_incoming_payments");
        jdbc.update("DELETE FROM shortcode_features");
        jdbc.update("DELETE FROM mpesa_shortcodes");
        shortcode = shortcode(ShortcodeType.PAYBILL);
        when(credentials.credentialFor(any())).thenReturn("encrypted");
        when(gateway.submit(any())).thenAnswer(call -> UUID.randomUUID());
    }

    // ---- feature switches

    @Test
    void everyFeatureStartsOffAndASwitchIsAudited() {
        List<ShortcodeFeatureDto> before = featureAdmin.list(shortcode.getUuid());
        assertThat(before).hasSize(5).allMatch(f -> !f.enabled() && f.updatedBy() == null);

        featureAdmin.update(shortcode.getUuid(), DarajaFeature.PULL_TRANSACTIONS, true);

        assertThat(featureAdmin.list(shortcode.getUuid())).filteredOn(ShortcodeFeatureDto::enabled)
                .extracting(ShortcodeFeatureDto::feature).containsExactly(DarajaFeature.PULL_TRANSACTIONS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'SHORTCODE_FEATURE_UPDATED'"
                + " AND entity_id = ?", Integer.class, shortcode.getUuid().toString())).isEqualTo(1);
    }

    @Test
    void everyFlowIsRefusedWhileItsFeatureIsOff() {
        assertThatThrownBy(() -> payouts.requestTax(shortcode.getUuid(), tax("3000")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("switched off");
        assertThatThrownBy(() -> payouts.requestPochi(shortcode.getUuid(), pochi("1500")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> checkouts.start(shortcode.getUuid(), checkout()))
                .isInstanceOf(ConflictException.class);
        verify(gateway, never()).submit(any());
        verify(daraja, never()).callProduct(any(), any(), any());
    }

    // ---- Tax Remittance

    @Test
    void anApprovedTaxRemittanceIsSentOnceAndSettledOnceByItsResult() throws Exception {
        features.set(shortcode.getUuid(), DarajaFeature.TAX_REMITTANCE, true, "admin");
        ApprovalExecutor.Approved approved = approve(() -> payouts.requestTax(shortcode.getUuid(), tax("3000")));
        accept("OC-TAX-1", "AG_TAX_1");

        ApprovalExecutor.Outcome outcome = taxExecutor.execute(approved);
        ApprovalExecutor.Outcome again = taxExecutor.execute(approved);

        assertThat(outcome.sent()).isTrue();
        assertThat(again.message()).startsWith("Already sent");
        Map<String, Object> body = sentBody(1);
        assertThat(body).containsEntry("CommandID", "PayTaxToKRA").containsEntry("PartyB", "572572")
                .containsEntry("AccountReference", "PRN1234XN").containsEntry("Amount", "3000")
                .containsEntry("PartyA", shortcode.getShortcode())
                .containsEntry("ResultURL", "https://pay.example.test/api/v1/callbacks/tax-remittance/result")
                .containsEntry("QueueTimeOutURL", "https://pay.example.test/api/v1/callbacks/tax-remittance/timeout");

        JsonNode result = objectMapper.readTree(TAX_SUCCESS.formatted("OC-TAX-1"));
        payouts.applyResult(DarajaFeature.TAX_REMITTANCE, result, result.toString());
        JsonNode failure = objectMapper.readTree("""
                {"Result":{"ResultCode":2001,"ResultDesc":"The initiator information is invalid.",
                "OriginatorConversationID":"OC-TAX-1","ConversationID":"AG_TAX_1"}}""");
        payouts.applyResult(DarajaFeature.TAX_REMITTANCE, failure, failure.toString());

        ProductPayout row = payoutRepository.findByApprovalRequestUuid(approved.requestUuid()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(ProductPayoutStatus.SUCCESS);
        assertThat(row.getTransactionId()).isEqualTo("QKA81LK5CY");
        assertThat(row.getReceiverName()).isEqualTo("00000 - Tax Collecting Company");
        assertThat(jdbc.queryForObject("SELECT raw_result->'Result'->>'TransactionID' FROM product_payouts",
                String.class)).isEqualTo("QKA81LK5CY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'PRODUCT_PAYOUT_SENT'"
                + " AND entity_id = ?", Integer.class, row.getUuid().toString())).isEqualTo(1);
    }

    @Test
    void noAnswerAndATimeoutLeaveTheOutcomeUnknownAndNothingIsResent() throws Exception {
        features.set(shortcode.getUuid(), DarajaFeature.TAX_REMITTANCE, true, "admin");
        ApprovalExecutor.Approved approved = approve(() -> payouts.requestTax(shortcode.getUuid(), tax("500")));
        when(daraja.callProduct(any(), any(), any())).thenReturn(
                MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "read timed out", 503));

        ApprovalExecutor.Outcome outcome = taxExecutor.execute(approved);
        taxExecutor.execute(approved);

        assertThat(outcome.sent()).isTrue();
        assertThat(outcome.message()).contains("do not raise it again");
        verify(daraja, times(1)).callProduct(any(), any(), any());
        ProductPayout row = payoutRepository.findByApprovalRequestUuid(approved.requestUuid()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(ProductPayoutStatus.PENDING);
        assertThat(row.isOutcomeUnknown()).isTrue();
    }

    @Test
    void aQueueTimeoutKeepsTheRemittancePendingAndFlagged() throws Exception {
        features.set(shortcode.getUuid(), DarajaFeature.TAX_REMITTANCE, true, "admin");
        ApprovalExecutor.Approved approved = approve(() -> payouts.requestTax(shortcode.getUuid(), tax("500")));
        accept("OC-TAX-2", "AG_TAX_2");
        taxExecutor.execute(approved);

        payouts.applyTimeout(DarajaFeature.TAX_REMITTANCE, objectMapper.readTree("""
                {"Result":{"ResultDesc":"The service request timed out.","ConversationID":"AG_TAX_2"}}"""));

        ProductPayout row = payoutRepository.findByApprovalRequestUuid(approved.requestUuid()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(ProductPayoutStatus.PENDING);
        assertThat(row.isOutcomeUnknown()).isTrue();
        assertThat(row.getTimedOutAt()).isNotNull();
    }

    @Test
    void aRefusalIsFinalAndASwitchedOffFeatureStopsTheExecutor() {
        features.set(shortcode.getUuid(), DarajaFeature.TAX_REMITTANCE, true, "admin");
        ApprovalExecutor.Approved refused = approve(() -> payouts.requestTax(shortcode.getUuid(), tax("700")));
        ApprovalExecutor.Approved later = approve(() -> payouts.requestTax(shortcode.getUuid(), tax("800")));
        when(daraja.callProduct(any(), any(), any())).thenReturn(
                MpesaApiResponse.error("400.002.02", "Bad Request - Invalid PartyB", 400));

        assertThat(taxExecutor.execute(refused).sent()).isFalse();
        features.set(shortcode.getUuid(), DarajaFeature.TAX_REMITTANCE, false, "admin");
        ApprovalExecutor.Outcome off = taxExecutor.execute(later);

        assertThat(payoutRepository.findByApprovalRequestUuid(refused.requestUuid()).orElseThrow().getStatus())
                .isEqualTo(ProductPayoutStatus.FAILED);
        assertThat(off.sent()).isFalse();
        assertThat(payoutRepository.findByApprovalRequestUuid(later.requestUuid())).isEmpty();
    }

    // ---- Business to Pochi

    @Test
    void aPochiPaymentCarriesOurOriginatorIdAndSettlesOnce() throws Exception {
        features.set(shortcode.getUuid(), DarajaFeature.BUSINESS_TO_POCHI, true, "admin");
        ApprovalExecutor.Approved approved = approve(() -> payouts.requestPochi(shortcode.getUuid(), pochi("1500")));
        assertThat(approved.payload().get("phone_number").asText()).isEqualTo("254705912645");
        accept(null, "AG_POCHI_1");

        assertThat(pochiExecutor.execute(approved).sent()).isTrue();
        Map<String, Object> body = sentBody(1);
        String originator = (String) body.get("OriginatorConversationID");
        assertThat(originator).startsWith("sar-pochi-");
        assertThat(body).containsEntry("CommandID", "BusinessPayToPochi").containsEntry("PartyB", "254705912645")
                .containsEntry("Amount", "1500").containsEntry("Occassion", "October stock")
                .containsEntry("ResultURL", "https://pay.example.test/api/v1/callbacks/pochi/result");

        // The success sample on developer.safaricom.co.ke/apis/BusinessToPochi.
        JsonNode result = objectMapper.readTree("""
                {"Result":{"ResultType":0,"ResultCode":0,"ResultDesc":"The service request is processed successfully.",
                "OriginatorConversationID":"%s","ConversationID":"AG_20240706_2010364430d9bbdaf872",
                "TransactionID":"SG632NMUAB","ResultParameters":{"ResultParameter":[
                {"Key":"TransactionAmount","Value":10},{"Key":"TransactionReceipt","Value":"SG632NMUAB"},
                {"Key":"ReceiverPartyPublicName","Value":"254705912645 - NICHOLAS JOHN SONGOK"}]}}}"""
                .formatted(originator));
        payouts.applyResult(DarajaFeature.BUSINESS_TO_POCHI, result, result.toString());
        payouts.applyResult(DarajaFeature.TAX_REMITTANCE, result, result.toString());

        ProductPayoutDto listed = payouts.list(shortcode.getUuid(), DarajaFeature.BUSINESS_TO_POCHI, null,
                PageRequest.of(0, 25)).getContent().getFirst();
        assertThat(listed.status()).isEqualTo(ProductPayoutStatus.SUCCESS);
        assertThat(listed.transactionId()).isEqualTo("SG632NMUAB");
        assertThat(listed.partyB()).isEqualTo("2547•••••645");
        assertThat(listed.receiverName()).isEqualTo("2547•••••645 - N. SONGOK");
        assertThat(payouts.list(shortcode.getUuid(), DarajaFeature.TAX_REMITTANCE, null, PageRequest.of(0, 25)))
                .isEmpty();
    }

    // ---- B2B Express Checkout

    @Test
    void aCheckoutSuccessBecomesOneIncomingPaymentAndOneEvent() throws Exception {
        features.set(shortcode.getUuid(), DarajaFeature.B2B_EXPRESS_CHECKOUT, true, "admin");
        when(daraja.callProduct(any(), any(), any())).thenReturn(MpesaApiResponse.success(
                objectMapper.readTree("{\"code\":\"0\",\"status\":\"USSD Initiated Successfully\"}"), 200));

        B2bCheckoutDto started = checkouts.start(shortcode.getUuid(), checkout());

        assertThat(started.status()).isEqualTo(B2bCheckoutStatus.PENDING);
        Map<String, Object> body = sentBody(1);
        assertThat(body).containsEntry("primaryShortCode", "000001")
                .containsEntry("receiverShortCode", shortcode.getShortcode())
                .containsEntry("RequestRefID", started.requestRefId().toString())
                .containsEntry("callbackUrl", "https://pay.example.test/api/v1/callbacks/b2b-checkout/result")
                .containsEntry("partnerName", shortcode.getBusinessName());

        // The success callback sample on developer.safaricom.co.ke/apis/B2BExpressCheckout.
        JsonNode callback = objectMapper.readTree("""
                {"resultCode":"0","resultDesc":"The service request is processed successfully.","amount":"71.0",
                "requestId":"%s","resultType":"0","conversationID":"AG_20230426_2010434680d9f5a73766",
                "transactionId":"RDQ01NFT1Q","status":"SUCCESS"}""".formatted(started.requestRefId()));
        checkouts.applyResult(callback, callback.toString());
        checkouts.applyResult(callback, callback.toString());

        B2bCheckoutDto settled = checkouts.list(shortcode.getUuid(), null, PageRequest.of(0, 25)).getContent().getFirst();
        assertThat(settled.status()).isEqualTo(B2bCheckoutStatus.SUCCESS);
        assertThat(settled.incomingPaymentUuid()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mpesa_incoming_payments WHERE transaction_id = 'RDQ01NFT1Q'"
                + " AND payment_type = 'B2B_EXPRESS_CHECKOUT' AND status = 'SUCCESS' AND amount = 71", Integer.class))
                .isEqualTo(1);
        assertThat(events.stream(PaymentEvent.class).filter(e -> e.type().equals(B2bCheckoutService.EVENT_COMPLETED)))
                .hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'B2B_EXPRESS_CHECKOUT_STARTED'"
                + " AND entity_id = ?", Integer.class, started.uuid().toString())).isEqualTo(1);
    }

    @Test
    void aCancelledCheckoutCreatesNoPaymentAndARefusedPushIs502() throws Exception {
        features.set(shortcode.getUuid(), DarajaFeature.B2B_EXPRESS_CHECKOUT, true, "admin");
        when(daraja.callProduct(any(), any(), any())).thenReturn(MpesaApiResponse.success(
                objectMapper.readTree("{\"code\":\"0\",\"status\":\"USSD Initiated Successfully\"}"), 200));
        B2bCheckoutDto started = checkouts.start(shortcode.getUuid(), checkout());

        JsonNode cancelled = objectMapper.readTree("""
                {"resultCode":"4001","resultDesc":"User cancelled transaction","requestId":"%s","amount":"71.0",
                "paymentReference":"MAndbubry3hi"}""".formatted(started.requestRefId()));
        checkouts.applyResult(cancelled, cancelled.toString());

        assertThat(checkouts.list(shortcode.getUuid(), B2bCheckoutStatus.CANCELLED, PageRequest.of(0, 25)))
                .hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mpesa_incoming_payments", Integer.class)).isZero();

        when(daraja.callProduct(any(), any(), any())).thenReturn(
                MpesaApiResponse.error("400.002.02", "Bad Request - Invalid primaryShortCode", 400));
        assertThatThrownBy(() -> checkouts.start(shortcode.getUuid(), checkout()))
                .isInstanceOf(UpstreamException.class).hasMessageContaining("Invalid primaryShortCode");
        assertThat(checkouts.list(shortcode.getUuid(), B2bCheckoutStatus.FAILED, PageRequest.of(0, 25))).hasSize(1);
    }

    @Test
    void aCheckoutNeedsAPaybill() {
        MpesaShortCode till = shortcode(ShortcodeType.TILL);
        features.set(till.getUuid(), DarajaFeature.B2B_EXPRESS_CHECKOUT, true, "admin");
        assertThatThrownBy(() -> checkouts.start(till.getUuid(), checkout()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("paybill");
    }

    // ---- helpers

    private ApprovalExecutor.Approved approve(Runnable submit) {
        org.mockito.Mockito.clearInvocations(gateway);
        submit.run();
        ArgumentCaptor<ApprovalSubmission> captor = ArgumentCaptor.forClass(ApprovalSubmission.class);
        verify(gateway).submit(captor.capture());
        ApprovalSubmission submission = captor.getValue();
        return new ApprovalExecutor.Approved(UUID.randomUUID(), submission.type(), submission.shortcodeUuid(),
                submission.amount(), submission.payload(), "maker", "checker");
    }

    private void accept(String originatorConversationId, String conversationId) throws Exception {
        String reply = originatorConversationId == null
                ? "{\"ConversationID\":\"%s\",\"ResponseCode\":\"0\",\"ResponseDescription\":\"Accept the service request successfully.\"}"
                .formatted(conversationId)
                : ("{\"OriginatorConversationID\":\"%s\",\"ConversationID\":\"%s\",\"ResponseCode\":\"0\","
                + "\"ResponseDescription\":\"Accept the service request successfully.\"}")
                .formatted(originatorConversationId, conversationId);
        when(daraja.callProduct(any(), any(), any())).thenReturn(MpesaApiResponse.success(objectMapper.readTree(reply),
                200));
    }

    // Runs the captured call against a fake HTTP client to see the body this service would send.
    @SuppressWarnings("unchecked")
    private Map<String, Object> sentBody(int calls) {
        ArgumentCaptor<BiFunction<MpesaDarajaHttpClient, String, JsonNode>> call = ArgumentCaptor.forClass(BiFunction.class);
        verify(daraja, times(calls)).callProduct(any(), anyString(), call.capture());
        MpesaDarajaHttpClient client = mock(MpesaDarajaHttpClient.class);
        call.getValue().apply(client, "Bearer token");
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(client).postProduct(any(URI.class), org.mockito.ArgumentMatchers.eq("Bearer token"), body.capture());
        return body.getValue();
    }

    private static RequestTaxRemittanceRequest tax(String amount) {
        return new RequestTaxRemittanceRequest(new BigDecimal(amount), "PRN1234XN", null, "October VAT");
    }

    private static RequestPochiPayoutRequest pochi(String amount) {
        return new RequestPochiPayoutRequest("254705912645", new BigDecimal(amount), "Supplier payment",
                "October stock", "Stock");
    }

    private static StartB2bCheckoutRequest checkout() {
        return new StartB2bCheckoutRequest("000001", new BigDecimal("71"), "INV-1", null);
    }

    private MpesaShortCode shortcode(ShortcodeType type) {
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(String.valueOf(600000 + (++sequence)));
        code.setShortcodeType(type);
        code.setBusinessName("Elimika Fees " + sequence);
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setCallbackUrl("https://example.test/api/v1/callbacks/stk/callback");
        code.setEnvironment(Environment.SANDBOX);
        code.setInitiatorName("api-operator");
        code.setIsActive(true);
        return shortCodeRepository.saveAndFlush(code);
    }
}
