package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;

/** Turns MpesaDarajaService's error envelope into the 502/503 an admin endpoint answers. */
public final class DarajaErrors {

    private DarajaErrors() {
    }

    public static UpstreamException toUpstream(MpesaErrorResponse error, String action) {
        String code = error != null ? error.errorCode() : null;
        if ("AUTH_FAILED".equals(code)) {
            return UpstreamException.badGateway(
                    "Daraja did not issue a token for this shortcode's credentials, so " + action + " was not sent");
        }
        if (code == null || "INTERNAL_ERROR".equals(code) || MpesaErrorResponse.NETWORK_ERROR.equals(code)) {
            return UpstreamException.unavailable("Safaricom could not be reached to " + action + "; try again shortly");
        }
        return UpstreamException.badGateway("Safaricom refused to " + action + describe(error.errorMessage()));
    }

    private static String describe(String message) {
        return message == null || message.isBlank() ? "" : " (" + message + ")";
    }
}
