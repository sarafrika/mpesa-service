package com.sarafrika.apps.mpesaservice.events;

import java.util.Map;
import java.util.UUID;

// Published via ApplicationEventPublisher when a payment settles; listeners such as webhooks react after commit.
// clientId is the owning product's azp, when there is one.
public record PaymentEvent(String type, UUID shortcodeUuid, UUID paymentUuid, String clientId, Map<String, Object> data) {

    public static final String C2B_CONFIRMED = "c2b.confirmed";
    public static final String STK_COMPLETED = "stk.completed";
    public static final String STK_FAILED = "stk.failed";
    public static final String STK_CANCELLED = "stk.cancelled";
    public static final String B2C_COMPLETED = "b2c.completed";
    public static final String B2C_FAILED = "b2c.failed";
    public static final String REVERSAL_COMPLETED = "reversal.completed";
    public static final String REVERSAL_FAILED = "reversal.failed";
}
