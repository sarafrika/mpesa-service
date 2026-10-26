package com.sarafrika.apps.mpesaservice.services.approvals;

import java.util.UUID;

// Features that move money submit here instead of calling Daraja; returns the new request's uuid.
public interface ApprovalGateway {

    UUID submit(ApprovalSubmission submission);
}
