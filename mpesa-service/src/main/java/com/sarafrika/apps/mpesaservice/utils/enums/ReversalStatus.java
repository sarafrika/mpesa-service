package com.sarafrika.apps.mpesaservice.utils.enums;

/** OUTCOME_UNKNOWN: Safaricom may have the request (no answer, or a queue timeout); never send it again. */
public enum ReversalStatus {
    WAITING_APPROVAL, CANCELLED, SENDING, SENT, OUTCOME_UNKNOWN, REVERSED, FAILED
}
