package com.sarafrika.apps.mpesaservice.utils.enums;

/** What a product may do with a shortcode it has been granted. */
public enum GrantOperation {
    /** Start STK pushes and receive stk.* webhooks for them. */
    STK_PUSH,
    /** Receive c2b.* webhooks for paybill and till deposits. */
    C2B_FEED,
    /** Send B2C payouts from the shortcode. */
    B2C_PAYOUT,
    /** Read payments made to the shortcode. */
    STATUS_LOOKUP
}
