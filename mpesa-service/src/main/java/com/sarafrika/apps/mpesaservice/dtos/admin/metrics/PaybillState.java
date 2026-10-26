package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

/** How a shortcode looks right now, worst first when more than one applies; DRAFT was never activated. */
public enum PaybillState {
    DRAFT, SUSPENDED, SANDBOX, LOW_FLOAT, STALE, HEALTHY
}
