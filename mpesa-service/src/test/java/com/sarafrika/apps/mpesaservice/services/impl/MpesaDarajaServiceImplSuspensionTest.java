package com.sarafrika.apps.mpesaservice.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.dtos.C2BRegistrationResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaTokenResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkPushResponse;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.services.payout.SecurityCredentialService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** A suspended shortcode refuses STK push before Safaricom is called; C2B registration sends the response type. */
class MpesaDarajaServiceImplSuspensionTest {

    private final MpesaShortCodeRepository repository = mock(MpesaShortCodeRepository.class);
    private final MpesaDarajaHttpClient sandbox = mock(MpesaDarajaHttpClient.class);
    private final MpesaIncomingPaymentService payments = mock(MpesaIncomingPaymentService.class);
    private final MpesaDarajaServiceImpl service = new MpesaDarajaServiceImpl(repository, payments, sandbox, mock(MpesaDarajaHttpClient.class),
            mock(SecurityCredentialService.class), "https://pay.example.test/api/v1/callbacks");

    @Test
    void aSuspendedShortcodeRefusesStkPushWith503() {
        MpesaShortCode code = code(false);
        when(repository.findByUuid(code.getUuid())).thenReturn(Optional.of(code));

        MpesaApiResponse<StkPushResponse> response = service.initiateSTKPush(code.getUuid(), "254708374149",
                BigDecimal.TEN, "ELM-1", "Fees");

        assertThat(response.success()).isFalse();
        assertThat(response.httpStatus()).isEqualTo(503);
        assertThat(response.error().errorCode()).isEqualTo("SHORTCODE_SUSPENDED");
        verifyNoInteractions(sandbox);
    }

    @Test
    void anActiveShortcodeStillPushes() {
        MpesaShortCode code = code(true);
        when(repository.findByUuid(code.getUuid())).thenReturn(Optional.of(code));
        when(sandbox.getAccessToken(anyString(), anyString())).thenReturn(new MpesaTokenResponse("tok", 3599, "Bearer"));
        when(sandbox.initiateSTKPush(anyString(), any())).thenReturn(new StkPushResponse("m", "ws_CO_1", "0",
                "Success", "Check your phone"));

        assertThat(service.initiateSTKPush(code.getUuid(), "254708374149", BigDecimal.TEN, "ELM-1", "Fees")
                .success()).isTrue();
    }

    @Test
    void thePendingStkRowNamesTheProductWhoseTokenStartedIt() {
        MpesaShortCode code = code(true);
        when(repository.findByUuid(code.getUuid())).thenReturn(Optional.of(code));
        when(sandbox.getAccessToken(anyString(), anyString())).thenReturn(new MpesaTokenResponse("tok", 3599, "Bearer"));
        when(sandbox.initiateSTKPush(anyString(), any())).thenReturn(new StkPushResponse("m", "ws_CO_2", "0",
                "Success", "Check your phone"));
        Jwt token = Jwt.withTokenValue("t").header("alg", "none").claim("azp", "elimika-backend").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token, List.of()));
        try {
            service.initiateSTKPush(code.getUuid(), "254708374149", BigDecimal.TEN, "ELM-1", "Fees");
        } finally {
            SecurityContextHolder.clearContext();
        }

        ArgumentCaptor<MpesaIncomingPayment> saved = ArgumentCaptor.forClass(MpesaIncomingPayment.class);
        verify(payments).create(saved.capture());
        assertThat(saved.getValue().getClientId()).isEqualTo("elimika-backend");
    }

    @Test
    @SuppressWarnings("unchecked")
    void c2bRegistrationSendsTheRequestedResponseType() {
        MpesaShortCode code = code(true);
        when(repository.findByUuid(code.getUuid())).thenReturn(Optional.of(code));
        when(sandbox.getAccessToken(anyString(), anyString())).thenReturn(new MpesaTokenResponse("tok", 3599, "Bearer"));
        when(sandbox.registerC2BUrls(anyString(), any())).thenReturn(new C2BRegistrationResponse(null, "o", "Success"));

        service.registerC2BUrls(code.getUuid(), "https://c", "https://v", "Cancelled");

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(sandbox).registerC2BUrls(anyString(), payload.capture());
        assertThat(payload.getValue()).containsEntry("ResponseType", "Cancelled")
                .containsEntry("ConfirmationURL", "https://c");
    }

    private static MpesaShortCode code(boolean active) {
        MpesaShortCode code = new MpesaShortCode();
        code.setUuid(UUID.randomUUID());
        code.setShortcode("174379");
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setPasskey("passkey");
        code.setCallbackUrl("https://pay.example.test/api/v1/callbacks/stk/callback");
        code.setEnvironment(Environment.SANDBOX);
        code.setIsActive(active);
        return code;
    }
}
