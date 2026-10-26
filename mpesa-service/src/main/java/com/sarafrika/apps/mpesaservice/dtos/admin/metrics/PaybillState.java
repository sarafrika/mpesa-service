package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

/** How a shortcode looks right now, worst first when more than one applies; DRAFT was never activated and
 * CALLBACKS_FAILING has inbox failures in the last 24 h. */
public enum PaybillState {
    DRAFT, SUSPENDED, SANDBOX, LOW_FLOAT, STALE, CALLBACKS_FAILING, HEALTHY
}
