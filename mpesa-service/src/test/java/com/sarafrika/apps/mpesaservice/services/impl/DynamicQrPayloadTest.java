package com.sarafrika.apps.mpesaservice.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaTokenResponse;
import com.sarafrika.apps.mpesaservice.dtos.QRCodeResponse;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.services.payout.SecurityCredentialService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.QRTransactionType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The Dynamic QR request carries Daraja's documented fields: MerchantName, RefNo, Amount (numeric),
 * TrxCode, CPI and Size (a string); see the comment on MpesaDarajaServiceImpl.buildQRCodePayload. */
class DynamicQrPayloadTest {

    private final MpesaShortCodeRepository repository = mock(MpesaShortCodeRepository.class);
    private final MpesaDarajaHttpClient sandbox = mock(MpesaDarajaHttpClient.class);
    private final MpesaDarajaServiceImpl service = new MpesaDarajaServiceImpl(repository,
            mock(MpesaIncomingPaymentService.class), sandbox, mock(MpesaDarajaHttpClient.class),
            mock(SecurityCredentialService.class), "https://pay.example.test/api/v1/callbacks");

    @Test
    @SuppressWarnings("unchecked")
    void sendsTheDocumentedFieldsWithTheShortcodeAsCpi() {
        MpesaShortCode code = code();
        when(repository.findByUuid(code.getUuid())).thenReturn(Optional.of(code));
        when(sandbox.getAccessToken(anyString(), anyString())).thenReturn(new MpesaTokenResponse("tok", 3599, "Bearer"));
        when(sandbox.generateQRCode(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new QRCodeResponse("AG_20191219_000043fdf61864fe9ff5", "16738-27456357-1",
                        "QR Code Successfully Generated.", "iVBORw0KGgo="));

        MpesaApiResponse<QRCodeResponse> response = service.generateDynamicQRCode(code.getUuid(), "Elimika Fees",
                "ELM-004512", new BigDecimal("1500.00"), QRTransactionType.PAY_BILL, 300);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(sandbox).generateQRCode(org.mockito.ArgumentMatchers.eq("Bearer tok"), payload.capture());
        assertThat(payload.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of("MerchantName", "Elimika Fees",
                "RefNo", "ELM-004512", "Amount", new java.math.BigInteger("1500"), "TrxCode", "PB", "CPI", "600100",
                "Size", "300"));
        // Daraja's own sample success carries ResponseCode "AG_...", not "00": the image decides.
        assertThat(response.data().isSuccessful()).isTrue();
    }

    @Test
    void anAbsentAmountIsLeftOutRatherThanSentAsZero() {
        Map<String, Object> payload = MpesaDarajaServiceImpl.buildQRCodePayload(code(), "Shop", "INV-1", null,
                QRTransactionType.BUY_GOODS, null);
        assertThat(payload).doesNotContainKey("Amount").containsEntry("TrxCode", "BG").containsEntry("Size", "300");
    }

    private static MpesaShortCode code() {
        MpesaShortCode code = new MpesaShortCode();
        code.setUuid(UUID.randomUUID());
        code.setShortcode("600100");
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setEnvironment(Environment.SANDBOX);
        code.setIsActive(true);
        return code;
    }
}
