package com.sarafrika.apps.mpesaservice.services.approvals;

import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import java.util.UUID;

// Optional companion of an ApprovalExecutor: hears, inside the deciding transaction, that a request of its
// type was rejected or expired, so it can release what it held (a reversal, a batch).
public interface ApprovalLifecycle {

    String type();

    void closed(UUID approvalUuid, ApprovalStatus status);
}
