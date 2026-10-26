package com.sarafrika.apps.mpesaservice.services.approvals;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;

// A money movement a maker asks for; a checker who is not the maker must approve it before it runs.
public record ApprovalSubmission(String type, UUID shortcodeUuid, BigDecimal amount, String reason, JsonNode payload) {
}
