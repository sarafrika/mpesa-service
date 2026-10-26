package com.sarafrika.apps.mpesaservice.utils.enums;

/** SENT means the row became a B2C payout, whose own status says whether it was paid. */
public enum PayoutBatchItemStatus {
    QUEUED, SENT, REFUSED, ERROR
}
