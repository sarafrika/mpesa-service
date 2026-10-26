package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.dtos.MpesaTokenResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ConnectionTestResultDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/** Asks Daraja for a fresh OAuth token with a shortcode's credentials; never throws, always reports. */
@Component
@Slf4j
public class ShortcodeConnectionTester {

    private final MpesaDarajaHttpClient sandbox;
    private final MpesaDarajaHttpClient production;

    public ShortcodeConnectionTester(@Qualifier("sandboxHttpClient") MpesaDarajaHttpClient sandbox,
                                     @Qualifier("productionHttpClient") MpesaDarajaHttpClient production) {
        this.sandbox = sandbox;
        this.production = production;
    }

    public ConnectionTestResultDto test(MpesaShortCode code) {
        long start = System.nanoTime();
        String error;
        try {
            String basic = Base64.getEncoder().encodeToString(
                    (code.getConsumerKey() + ":" + code.getConsumerSecret()).getBytes(StandardCharsets.UTF_8));
            MpesaDarajaHttpClient client = code.getEnvironment() == Environment.PRODUCTION ? production : sandbox;
            MpesaTokenResponse token = client.getAccessToken("client_credentials", "Basic " + basic);
            error = token != null && token.isValid() ? null : "Daraja answered without a usable token";
        } catch (ResourceAccessException ex) {
            error = "Safaricom could not be reached; try again shortly";
        } catch (RuntimeException ex) {
            error = describe(ex.getMessage());
        }
        long latency = (System.nanoTime() - start) / 1_000_000;
        if (error != null) {
            log.warn("Daraja token check failed for shortcode {}: {}", code.getUuid(), error);
        }
        return new ConnectionTestResultDto(error == null, latency, error, LocalDateTime.now());
    }

    // MpesaClientConfig turns HTTP errors into "Client error: 401 UNAUTHORIZED" and "Server error: ..."
    static String describe(String message) {
        String text = message == null ? "" : message;
        if (text.startsWith("Client error")) {
            return "Daraja refused these credentials (" + text.substring(text.indexOf(':') + 1).trim() + ")";
        }
        if (text.startsWith("Server error")) {
            return "Daraja failed to issue a token (" + text.substring(text.indexOf(':') + 1).trim() + ")";
        }
        return "The token request failed";
    }
}
