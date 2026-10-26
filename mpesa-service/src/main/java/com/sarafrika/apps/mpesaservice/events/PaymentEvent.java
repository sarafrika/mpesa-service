package com.sarafrika.apps.mpesaservice.events;

import java.util.Map;
import java.util.UUID;

/**
 * Raised with Spring's ApplicationEventPublisher when money moves or a payment settles; listeners
 * (webhooks, metrics) react after commit. {@code clientId} is the product's azp when one owns the payment.
 */
public record PaymentEvent(String type, UUID shortcodeUuid, UUID paymentUuid, String clientId, Map<String, Object> data) {

    public static final String C2B_CONFIRMED = "c2b.confirmed";
    public static final String STK_COMPLETED = "stk.completed";
    public static final String STK_FAILED = "stk.failed";
    public static final String STK_CANCELLED = "stk.cancelled";
    public static final String B2C_COMPLETED = "b2c.completed";
    public static final String B2C_FAILED = "b2c.failed";
}
