package com.sarafrika.apps.mpesaservice.services.webhooks;

import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The event types a subscription can ask for, and the grant that entitles a product to each family. */
public final class WebhookEvents {

    /** Sent only by "Send test event"; it ignores the subscription's event filter. */
    public static final String TEST = "test.ping";

    public static final List<String> SUBSCRIBABLE = List.of(
            PaymentEvent.C2B_CONFIRMED, PaymentEvent.STK_COMPLETED, PaymentEvent.STK_FAILED,
            PaymentEvent.STK_CANCELLED, PaymentEvent.B2C_COMPLETED, PaymentEvent.B2C_FAILED,
            PaymentEvent.REVERSAL_COMPLETED, PaymentEvent.REVERSAL_FAILED);

    private static final List<String> REFERENCE_KEYS = List.of(
            "transaction_id", "mpesa_receipt_number", "checkout_request_id", "client_reference",
            "originator_conversation_id", "bill_ref_number", "account_reference");

    private WebhookEvents() {
    }

    public static boolean isSubscribable(String type) {
        return SUBSCRIBABLE.contains(type);
    }

    /** c2b.* needs C2B_FEED, stk.* needs STK_PUSH; b2c.* goes only to the paying client; reversal.* goes to the
     * product that owns the reversed payment (see WebhookFanOutService). */
    public static Optional<GrantOperation> requiredGrant(String type) {
        if (type == null) {
            return Optional.empty();
        }
        if (type.startsWith("c2b.")) {
            return Optional.of(GrantOperation.C2B_FEED);
        }
        if (type.startsWith("stk.")) {
            return Optional.of(GrantOperation.STK_PUSH);
        }
        if (type.startsWith("b2c.")) {
            return Optional.of(GrantOperation.B2C_PAYOUT);
        }
        return Optional.empty();
    }

    public static boolean isReversal(String type) {
        return type != null && type.startsWith("reversal.");
    }

    public static boolean isPayout(String type) {
        return type != null && type.startsWith("b2c.");
    }

    /** A short id people recognise: the M-Pesa receipt when there is one, else the request reference. */
    public static String reference(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        for (String key : REFERENCE_KEYS) {
            Object value = data.get(key);
            if (value != null && !value.toString().isBlank()) {
                String text = value.toString();
                return text.length() > 100 ? text.substring(0, 100) : text;
            }
        }
        return null;
    }

    static Set<String> validated(List<String> types) {
        if (types == null) {
            return Set.of();
        }
        for (String type : types) {
            if (!isSubscribable(type)) {
                throw new InvalidRequestException("event_types",
                        "unknown event type '" + type + "'; allowed: " + String.join(", ", SUBSCRIBABLE));
            }
        }
        return Set.copyOf(types);
    }
}
