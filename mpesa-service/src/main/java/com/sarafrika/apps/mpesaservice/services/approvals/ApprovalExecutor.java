package com.sarafrika.apps.mpesaservice.services.approvals;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;

// One Spring bean per request type (REVERSAL, BULK_PAYOUT, B2B_SWEEP); runs an approved request.
public interface ApprovalExecutor {

    String type();

    /** Starts the movement; Daraja answers later, so this returns once the request is sent or refused. */
    Outcome execute(Approved request);

    record Approved(UUID requestUuid, String type, UUID shortcodeUuid, BigDecimal amount, JsonNode payload,
                    String maker, String checker) {
    }

    record Outcome(boolean sent, String message) {
    }
}
