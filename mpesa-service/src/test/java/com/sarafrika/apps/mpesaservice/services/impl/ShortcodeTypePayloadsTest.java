package com.sarafrika.apps.mpesaservice.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.dtos.B2CPaymentResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkPushResponse;
import com.sarafrika.apps.mpesaservice.dtos.TransactionReversalResponse;
import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.services.payout.SecurityCredentialService;
import com.sarafrika.apps.mpesaservice.services.shortcode.CallbackUrls;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.QRTransactionType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which number each Daraja call carries per shortcode type (STK FAQ 10 for tills), and that a type which
 * cannot do something is refused with 409 before any token is fetched. */
class ShortcodeTypePayloadsTest {

    private final MpesaShortCodeRepository repository = mock(MpesaShortCodeRepository.class);
    private final MpesaDarajaHttpClient sandbox = mock(MpesaDarajaHttpClient.class);
    private final MpesaDarajaServiceImpl service = new MpesaDarajaServiceImpl(repository,
            mock(MpesaIncomingPaymentService.class), sandbox, mock(MpesaDarajaHttpClient.class),
            mock(SecurityCredentialService.class), new CallbackUrls("https://pay.example.test/api/v1/callbacks"));

    @Test
    void aTillPushesBuyGoodsUnderItsStoreNumberToTheTill() {
        Map<String, Object> stk = service.buildSTKPushPayload(till(), "254708374149", BigDecimal.TEN, null, null);

        assertThat(stk).containsEntry("BusinessShortCode", "174300").containsEntry("PartyB", "5123456")
                .containsEntry("TransactionType", "CustomerBuyGoodsOnline");
        String password = new String(Base64.getDecoder().decode((String) stk.get("Password")), StandardCharsets.UTF_8);
        assertThat(password).isEqualTo("174300" + "pk" + stk.get("Timestamp"));
        assertThat(service.buildSTKStatusPayload(till(), "ws_CO_1")).containsEntry("BusinessShortCode", "174300");
    }

    @Test
    void aPaybillAndAOneAccountPushAsPaybills() {
        for (ShortcodeType type : new ShortcodeType[] {ShortcodeType.PAYBILL, ShortcodeType.ONE_ACCOUNT}) {
            MpesaShortCode code = code(type, "600100", null);
            assertThat(service.buildSTKPushPayload(code, "254708374149", BigDecimal.TEN, "ELM-1", null))
                    .containsEntry("BusinessShortCode", "600100").containsEntry("PartyB", "600100")
                    .containsEntry("TransactionType", "CustomerPayBillOnline");
        }
    }

    @Test
    void tillQueriesRegistrationAndReversalUseTheGoLiveNumberWithIdentifierType4() {
        MpesaShortCode till = till();

        assertThat(service.buildTransactionStatusPayload(till, "TJQ1", null)).containsEntry("PartyA", "174300")
                .containsEntry("IdentifierType", "4");
        assertThat(service.buildAccountBalancePayload(till, null)).containsEntry("PartyA", "174300")
                .containsEntry("IdentifierType", "4");
        assertThat(service.buildC2BRegistrationPayload(till, "https://c", "https://v", "Completed"))
                .containsEntry("ShortCode", "174300");
        assertThat(service.buildTransactionReversalPayload(till, "TJQ1", BigDecimal.TEN, "Refund"))
                .containsEntry("ReceiverParty", "174300");
        assertThat(MpesaDarajaServiceImpl.buildQRCodePayload(till, "Shop", "R1", BigDecimal.TEN,
                QRTransactionType.BUY_GOODS, 300)).containsEntry("CPI", "5123456").containsEntry("TrxCode", "BG");
    }

    @Test
    void aTypeThatCannotDoSomethingIsRefusedBeforeAnyToken() {
        MpesaShortCode b2c = code(ShortcodeType.B2C, "600200", null);
        MpesaShortCode till = till();
        when(repository.findByUuid(b2c.getUuid())).thenReturn(Optional.of(b2c));

        MpesaApiResponse<StkPushResponse> stk = service.initiateSTKPush(b2c.getUuid(), "254708374149",
                BigDecimal.TEN, "A", "B");
        MpesaApiResponse<B2CPaymentResponse> payout = service.sendB2CPayout(till, "sar-1", "254708374149",
                BigDecimal.TEN, null, null);
        MpesaApiResponse<TransactionReversalResponse> reversal = service.reverseTransaction(b2c, "TJQ1",
                BigDecimal.TEN, "Refund");

        assertThat(stk.httpStatus()).isEqualTo(409);
        assertThat(stk.error().errorCode()).isEqualTo(CapabilityNotSupportedException.CODE);
        assertThat(payout.error().errorMessage()).contains("till (Buy Goods)").contains("B2C payouts");
        assertThat(reversal.error().errorMessage()).contains("by hand");
        verifyNoInteractions(sandbox);
    }

    private static MpesaShortCode till() {
        return code(ShortcodeType.TILL, "5123456", "174300");
    }

    private static MpesaShortCode code(ShortcodeType type, String number, String store) {
        MpesaShortCode code = new MpesaShortCode();
        code.setUuid(UUID.randomUUID());
        code.setShortcode(number);
        code.setStoreNumber(store);
        code.setShortcodeType(type);
        code.setBusinessName("Shop");
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setPasskey("pk");
        code.setInitiatorName("api");
        code.setEnvironment(Environment.SANDBOX);
        code.setIsActive(true);
        code.setCallbackToken(MpesaShortCode.newCallbackToken());
        return code;
    }
}
