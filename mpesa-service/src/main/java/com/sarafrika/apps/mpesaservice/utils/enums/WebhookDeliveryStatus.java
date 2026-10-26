package com.sarafrika.apps.mpesaservice.utils.enums;

/** PENDING until the receiver answers 2xx (DELIVERED) or every retry is used up (FAILED). */
public enum WebhookDeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED
}
