package com.sarafrika.apps.mpesaservice.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.dtos.B2BPaymentRequest;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.services.payout.SecurityCredentialService;
import com.sarafrika.apps.mpesaservice.services.shortcode.CallbackUrls;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bReceiverType;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The B2C Account Top Up body (developer.safaricom.co.ke/apis/B2CAccountTopUp) and that sweeps are unchanged. */
class B2bPaymentPayloadTest {

    private final SecurityCredentialService credentials = mock(SecurityCredentialService.class);
    private final MpesaDarajaServiceImpl service = new MpesaDarajaServiceImpl(mock(MpesaShortCodeRepository.class),
            mock(MpesaIncomingPaymentService.class), mock(MpesaDarajaHttpClient.class),
            mock(MpesaDarajaHttpClient.class), credentials, new CallbackUrls("https://pay.example.test/api/v1/callbacks"));

    @Test
    void aTopUpSendsBusinessPayToBulkFromOurPaybillToOurB2cShortcode() {
        MpesaShortCode paybill = paybill();
        when(credentials.credentialFor(paybill)).thenReturn("encrypted");

        Map<String, Object> body = service.buildB2BPaymentPayload(paybill, new B2BPaymentRequest(
                B2bReceiverType.B2C_SHORTCODE.commandId(), "4954124", new BigDecimal("50000.00"), "TOP-7K2Q9XHD",
                "B2C top-up TOP-7K2Q9XHD"));

        assertThat(body).containsEntry("Initiator", "sarafrika-api").containsEntry("SecurityCredential", "encrypted")
                .containsEntry("CommandID", "BusinessPayToBulk").containsEntry("SenderIdentifierType", "4")
                .containsEntry("RecieverIdentifierType", "4").containsEntry("Amount", "50000")
                .containsEntry("PartyA", "4953892").containsEntry("PartyB", "4954124")
                .containsEntry("AccountReference", "TOP-7K2Q9XHD").containsEntry("Remarks", "B2C top-up TOP-7K2Q9XHD")
                .doesNotContainKey("Requester");
        assertThat((String) body.get("ResultURL")).endsWith("/b2b/result");
        assertThat((String) body.get("QueueTimeOutURL")).endsWith("/b2b/timeout");
    }

    @Test
    void remarksAreTrimmedToTheDocumentedHundredCharacters() {
        Map<String, Object> body = service.buildB2BPaymentPayload(paybill(), new B2BPaymentRequest(
                "BusinessPayToBulk", "4954124", BigDecimal.TEN, "TOP-1", "x".repeat(150)));

        assertThat((String) body.get("Remarks")).hasSize(100);
    }

    @Test
    void aPaybillSweepStillCarriesItsAccountAndATillNone() {
        assertThat(service.buildB2BPaymentPayload(paybill(), new B2BPaymentRequest("BusinessPayBill", "522522",
                BigDecimal.TEN, "SAR-001", "Sweep SWP-1"))).containsEntry("CommandID", "BusinessPayBill")
                .containsEntry("AccountReference", "SAR-001");
        assertThat(service.buildB2BPaymentPayload(paybill(), new B2BPaymentRequest("BusinessBuyGoods", "123456",
                BigDecimal.TEN, null, null))).doesNotContainKey("AccountReference").containsEntry("Remarks", "Sweep");
    }

    private static MpesaShortCode paybill() {
        MpesaShortCode code = new MpesaShortCode();
        code.setUuid(UUID.randomUUID());
        code.setShortcode("4953892");
        code.setShortcodeType(ShortcodeType.PAYBILL);
        code.setEnvironment(Environment.PRODUCTION);
        code.setInitiatorName("sarafrika-api");
        code.setCallbackToken("token");
        return code;
    }
}
