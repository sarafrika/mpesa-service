package com.sarafrika.apps.mpesaservice.utils.enums;

// MATCHED lines are counted on the run, not stored; only these need a person.
public enum ReconciliationExceptionKind {
    MISSING_IN_LEDGER, MISSING_IN_STATEMENT, AMOUNT_MISMATCH, DUPLICATE_IN_LEDGER
}
