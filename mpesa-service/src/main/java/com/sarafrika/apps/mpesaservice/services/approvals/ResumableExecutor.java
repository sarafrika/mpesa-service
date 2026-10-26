package com.sarafrika.apps.mpesaservice.services.approvals;

// Marks an executor that is safe to run again after a crash: it never resends what it already sent.
// An interrupted execution of any other type is marked FAILED with its outcome unknown instead.
public interface ResumableExecutor extends ApprovalExecutor {
}
