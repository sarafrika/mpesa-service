package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;

/** Reads how a handler did from the ack it gave Safaricom. Every handler answers 200 with a ResultCode:
 * "0" or "00000000" is success, anything else is a failure, except that a C2B validation's C2B000xx is
 * a decision (rejecting a payment), not a failure. A failure a handler reported through CallbackOutcome wins. */
final class CallbackAcks {

    record Processing(boolean ok, String error, boolean rejected) {
        Processing(boolean ok, String error) {
            this(ok, error, false);
        }

        static Processing success() {
            return new Processing(true, null);
        }

        static Processing failure(String error) {
            return new Processing(false, CallbackOutcome.shorten(error));
        }

        static Processing rejected(String why) {
            return new Processing(false, why, true);
        }
    }

    /** Statuses answered before any handler ran: unknown callback token, no such route. */
    static final java.util.Set<Integer> REFUSED = java.util.Set.of(401, 403, 404);

    static String refusedDescription(int httpStatus) {
        return "Refused with HTTP " + httpStatus + " before any handler ran: no shortcode here has this callback URL, "
                + "so nothing was applied. If it was Safaricom, re-register the shortcode's URLs.";
    }

    private CallbackAcks() {
    }

    static Processing read(CallbackKind kind, int httpStatus, JsonNode ack, String reported) {
        if (reported != null) {
            return Processing.failure(reported);
        }
        if (REFUSED.contains(httpStatus)) {
            return Processing.rejected(refusedDescription(httpStatus));
        }
        if (httpStatus >= 400) {
            return Processing.failure("Answered HTTP " + httpStatus);
        }
        JsonNode code = ack == null ? null : ack.get("ResultCode");
        if (code == null || code.isNull()) {
            return Processing.success();
        }
        String value = code.asText().trim();
        if (value.isEmpty() || value.chars().allMatch(c -> c == '0')) {
            return Processing.success();
        }
        if (kind == CallbackKind.C2B_VALIDATION && value.startsWith("C2B")) {
            return Processing.success();
        }
        JsonNode desc = ack.get("ResultDesc");
        return Processing.failure("Acknowledged with ResultCode " + value
                + (desc == null || desc.isNull() ? "" : ": " + desc.asText()));
    }
}
