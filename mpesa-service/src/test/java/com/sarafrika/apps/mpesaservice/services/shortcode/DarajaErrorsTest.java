package com.sarafrika.apps.mpesaservice.services.shortcode;

import static org.assertj.core.api.Assertions.assertThat;

import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import org.junit.jupiter.api.Test;

class DarajaErrorsTest {

    @Test
    void aMissingTokenSaysTheRequestWasNotSent() {
        UpstreamException error = DarajaErrors.toUpstream(
                new MpesaErrorResponse("AUTH_FAILED", "Failed to obtain access token", null, null),
                "register the C2B URLs");

        assertThat(error.getMessage()).isEqualTo("Daraja did not issue a token for this shortcode's credentials, "
                + "so the request to register the C2B URLs was not sent");
    }

    @Test
    void aRefusalQuotesSafaricomsReason() {
        UpstreamException error = DarajaErrors.toUpstream(
                new MpesaErrorResponse("400.002.02", "Bad Request - Invalid Amount", null, null),
                "send the test STK push");

        assertThat(error.getMessage()).isEqualTo("Safaricom refused to send the test STK push "
                + "(Bad Request - Invalid Amount)");
    }
}
