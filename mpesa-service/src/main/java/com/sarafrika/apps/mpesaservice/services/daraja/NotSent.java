package com.sarafrika.apps.mpesaservice.services.daraja;

import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;

/** A money request that failed on our side before reaching Safaricom: no Daraja token, a request we could not
 * build, or a shortcode type that cannot do it. It is final (nothing was sent), and is worded as ours. */
public final class NotSent {

    public static final String AUTH_FAILED = "AUTH_FAILED";
    public static final String PREFIX = "Not sent: ";

    private NotSent() {
    }

    /** True when the reply says nothing reached Safaricom. */
    public static boolean is(MpesaApiResponse<?> response) {
        if (response == null || response.success() || response.error() == null) {
            return false;
        }
        String code = response.error().errorCode();
        return AUTH_FAILED.equals(code) || MpesaErrorResponse.NOT_SENT.equals(code)
                || CapabilityNotSupportedException.CODE.equals(code);
    }

    /** Why it was not sent, with what to check; {@code what} names the request ("sweep", "payout"...). */
    public static String describe(MpesaApiResponse<?> response, String what) {
        MpesaErrorResponse error = response.error();
        if (AUTH_FAILED.equals(error.errorCode())) {
            return "We could not authenticate with Daraja (it issued no access token for this shortcode's consumer "
                    + "key and secret), so the " + what + " never reached Safaricom. Check the credentials and run "
                    + "the connection check, then raise it again.";
        }
        String reason = error.errorMessage() == null ? "the request could not be prepared" : error.errorMessage();
        if (MpesaErrorResponse.NOT_SENT.equals(error.errorCode())) {
            return reason + ". Nothing reached Safaricom; check the shortcode's initiator and security credential.";
        }
        return reason + " Nothing reached Safaricom.";
    }

    /** The outcome line an approval shows. */
    public static String outcome(String description) {
        return PREFIX + description;
    }

    /** True for a stored description written by {@link #describe}. */
    public static boolean wrote(String description) {
        return description != null && (description.startsWith("We could not authenticate with Daraja")
                || description.contains("Nothing reached Safaricom"));
    }
}
