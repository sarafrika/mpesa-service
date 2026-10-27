package com.sarafrika.apps.mpesaservice.services.daraja;

import static org.assertj.core.api.Assertions.assertThat;

import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;
import org.junit.jupiter.api.Test;

/** Which replies mean nothing reached Safaricom, and how they are worded. */
class NotSentTest {

    @Test
    void ourSideFailuresAreNotSentAndSafaricomsAnswersAreNot() {
        assertThat(NotSent.is(MpesaApiResponse.error("AUTH_FAILED", "Failed to obtain access token", 401))).isTrue();
        assertThat(NotSent.is(MpesaApiResponse.error(MpesaErrorResponse.NOT_SENT, "The payout could not be prepared: "
                + "no certificate", 422))).isTrue();
        assertThat(NotSent.is(MpesaApiResponse.error(CapabilityNotSupportedException.CODE, "No.", 409))).isTrue();
        assertThat(NotSent.is(MpesaApiResponse.error("400.002.02", "Bad Request - Invalid Amount", 400))).isFalse();
        assertThat(NotSent.is(MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "timed out", 503))).isFalse();
    }

    @Test
    void eachCauseGetsOurWordingAndAHint() {
        String auth = NotSent.describe(MpesaApiResponse.error("AUTH_FAILED", "x", 401), "sweep");
        String prepared = NotSent.describe(MpesaApiResponse.error(MpesaErrorResponse.NOT_SENT,
                "The B2B payment could not be prepared: no certificate", 422), "sweep");

        assertThat(auth).contains("the sweep never reached Safaricom").contains("Check the credentials");
        assertThat(prepared).contains("no certificate").contains("initiator and security credential");
        assertThat(NotSent.wrote(auth)).isTrue();
        assertThat(NotSent.wrote(prepared)).isTrue();
        assertThat(NotSent.wrote("Bad Request - Invalid Amount")).isFalse();
    }
}
