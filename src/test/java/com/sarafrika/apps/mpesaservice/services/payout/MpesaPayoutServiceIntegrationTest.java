package com.sarafrika.apps.mpesaservice.services.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.dtos.B2CPaymentResponse;
import com.sarafrika.apps.mpesaservice.dtos.B2CPayoutRequest;
import com.sarafrika.apps.mpesaservice.dtos.B2CResult;
import com.sarafrika.apps.mpesaservice.dtos.B2CResultParameter;
import com.sarafrika.apps.mpesaservice.dtos.B2CResultParameters;
import com.sarafrika.apps.mpesaservice.dtos.B2CTimeoutCallback;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
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
@Import(MpesaPayoutService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MpesaPayoutServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MpesaPayoutService service;
    @Autowired
    private MpesaShortCodeRepository shortCodeRepository;

    @MockitoBean
    private MpesaDarajaService darajaService;

    private MpesaShortCode shortcode;

    @BeforeEach
    void setUp() {
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(String.valueOf(600000 + (int) (Math.random() * 399999)));
        code.setShortcodeType(ShortcodeType.PAYBILL);
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
        return new B2CResult(0, 0, "The service request is processed successfully.", originator, "AG_1", "TJE4K9L2QX",
                new B2CResultParameters(List.of(
                        new B2CResultParameter("TransactionReceipt", "TJE4K9L2QX"),
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
