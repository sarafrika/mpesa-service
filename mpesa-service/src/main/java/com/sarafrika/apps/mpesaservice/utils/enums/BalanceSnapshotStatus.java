package com.sarafrika.apps.mpesaservice.utils.enums;

/** PENDING until Safaricom posts the Account Balance result (COMPLETED or FAILED) or a queue timeout. */
public enum BalanceSnapshotStatus {
    PENDING, COMPLETED, FAILED, TIMED_OUT
}
