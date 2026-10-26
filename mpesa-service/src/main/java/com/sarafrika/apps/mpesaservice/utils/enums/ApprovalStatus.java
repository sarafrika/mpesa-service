package com.sarafrika.apps.mpesaservice.utils.enums;

/** WAITING for a checker; APPROVED then EXECUTING while it runs; EXECUTED once sent (or FAILED if refused). */
public enum ApprovalStatus {
    WAITING, APPROVED, REJECTED, EXPIRED, EXECUTING, EXECUTED, FAILED
}
