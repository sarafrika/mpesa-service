package com.sarafrika.apps.mpesaservice.utils.enums;

/** Who asked for a balance: a person from the console, the scheduled query, or the service itself after a
 * successful B2B sweep or B2C top-up. */
public enum BalanceQuerySource {
    MANUAL, SCHEDULED, AFTER_TRANSFER
}
