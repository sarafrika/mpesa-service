package com.sarafrika.apps.mpesaservice.services.shortcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.dtos.MpesaTokenResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ConnectionTestResultDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/** The token check asks the right Daraja environment and turns every failure into a reason. */
class ShortcodeConnectionTesterTest {

    private final MpesaDarajaHttpClient sandbox = mock(MpesaDarajaHttpClient.class);
    private final MpesaDarajaHttpClient production = mock(MpesaDarajaHttpClient.class);
    private final ShortcodeConnectionTester tester = new ShortcodeConnectionTester(sandbox, production);

    @Test
    void aValidTokenIsOkAndUsesBasicAuthWithTheShortcodesKeys() {
        when(production.getAccessToken(eq("client_credentials"), anyString()))
                .thenReturn(new MpesaTokenResponse("tok", 3599, "Bearer"));

        ConnectionTestResultDto result = tester.test(code(Environment.PRODUCTION));

        assertThat(result.ok()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
        verify(production).getAccessToken("client_credentials", "Basic a2V5OnNlY3JldA==");
        verifyNoInteractions(sandbox);
    }

    @Test
    void refusalsOutagesAndEmptyAnswersAreReportedNotThrown() {
        MpesaShortCode code = code(Environment.SANDBOX);
        when(sandbox.getAccessToken(anyString(), anyString()))
                .thenThrow(new RuntimeException("Client error: 400 BAD_REQUEST"))
                .thenThrow(new ResourceAccessException("I/O error: connect timed out"))
                .thenThrow(new RuntimeException("Server error: 503 SERVICE_UNAVAILABLE"))
                .thenReturn(new MpesaTokenResponse("", 0, "Bearer"))
                .thenThrow(new IllegalStateException("boom with internals"));

        assertThat(tester.test(code).error()).isEqualTo("Daraja refused these credentials (400 BAD_REQUEST)");
        assertThat(tester.test(code).error()).isEqualTo("Safaricom could not be reached; try again shortly");
        assertThat(tester.test(code).error()).isEqualTo("Daraja failed to issue a token (503 SERVICE_UNAVAILABLE)");
        assertThat(tester.test(code).error()).isEqualTo("Daraja answered without a usable token");
        ConnectionTestResultDto last = tester.test(code);
        assertThat(last.ok()).isFalse();
        assertThat(last.error()).isEqualTo("The token request failed");
        assertThat(last.checkedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void secretsAreMaskedToTheirLastFourCharacters() {
        assertThat(ShortcodeViews.mask("abcdefQx7d")).isEqualTo("••••Qx7d");
        assertThat(ShortcodeViews.mask("abcd")).isEqualTo("••••");
        assertThat(ShortcodeViews.mask("")).isNull();
        assertThat(ShortcodeViews.mask(null)).isNull();
    }

    private static MpesaShortCode code(Environment environment) {
        MpesaShortCode code = new MpesaShortCode();
        code.setUuid(UUID.randomUUID());
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setEnvironment(environment);
        return code;
    }
}
