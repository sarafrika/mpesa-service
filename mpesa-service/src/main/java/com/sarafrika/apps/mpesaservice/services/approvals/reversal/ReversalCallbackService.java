package com.sarafrika.apps.mpesaservice.services.approvals.reversal;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarafrika.apps.mpesaservice.models.ReversalRequest;
import com.sarafrika.apps.mpesaservice.repositories.ApprovalRequestRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReversalRequestRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReversalStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settles reversals from Safaricom's result and timeout callbacks. Result codes per
 * developer.safaricom.co.ke/apis/Reversal: "0" reversed; "R000001" the transaction was already reversed
 * (so the payment counts as reversed); anything else failed. ResultCode may be a number or a string. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReversalCallbackService {

    static final String ALREADY_REVERSED = "R000001";
    static final List<ReversalStatus> OPEN = List.of(ReversalStatus.SENDING, ReversalStatus.SENT,
            ReversalStatus.OUTCOME_UNKNOWN);

    private final ReversalRequestRepository reversals;
    private final ApprovalRequestRepository approvals;

    /** body is the callback as received ({"Result": {...}}); the row is locked, so repeats settle it once. */
    @Transactional
    public Optional<ReversalRequest> applyResult(JsonNode body, String raw) {
        JsonNode result = body.has("Result") ? body.get("Result") : body;
        String code = text(result, "ResultCode");
        String desc = text(result, "ResultDesc");
        Map<String, String> params = parameters(result.path("ResultParameters").path("ResultParameter"));
        Optional<ReversalRequest> match = lock(text(result, "OriginatorConversationID"),
                text(result, "ConversationID"), params.get("OriginalTransactionID"));
        if (match.isEmpty()) {
            log.warn("Reversal result for unknown request {} / {}", text(result, "OriginatorConversationID"),
                    text(result, "ConversationID"));
            return Optional.empty();
        }
        ReversalRequest reversal = match.get();
        boolean refusedAtSubmit = reversal.getStatus() == ReversalStatus.FAILED && reversal.getRawCallback() == null;
        boolean success = "0".equals(code);
        if (!OPEN.contains(reversal.getStatus()) && !(refusedAtSubmit && (success || ALREADY_REVERSED.equals(code)))) {
            log.info("Ignoring repeat reversal result for {} already {}", reversal.getUuid(), reversal.getStatus());
            return match;
        }
        Instant now = Instant.now();
        reversal.setResultCode(clip(code, 20));
        reversal.setResultDesc(clip(desc, 500));
        reversal.setRawCallback(raw);
        reversal.setCompletedAt(now);
        String message;
        if (success) {
            reversal.setStatus(ReversalStatus.REVERSED);
            reversal.setReversalTransactionId(clip(text(result, "TransactionID"), 50));
            reversals.markPaymentReversed(reversal.getPaymentUuid(), now, reversal.getReversalTransactionId());
            message = "Safaricom reversed it (receipt " + reversal.getReversalTransactionId() + ").";
        } else {
            reversal.setStatus(ReversalStatus.FAILED);
            if (ALREADY_REVERSED.equals(code)) {
                reversals.markPaymentReversed(reversal.getPaymentUuid(), now, null);
            }
            message = "Safaricom refused the reversal: " + (desc == null ? code : desc);
        }
        ReversalRequest saved = reversals.save(reversal);
        approvals.lockByUuid(saved.getApprovalUuid()).ifPresent(approval -> {
            approval.setOutcomeMessage(message);
            if (!success && approval.getStatus() == ApprovalStatus.EXECUTED) {
                approval.setStatus(ApprovalStatus.FAILED);
            }
            approvals.save(approval);
        });
        return Optional.of(saved);
    }

    /** A queue timeout leaves the outcome unknown; the reversal is flagged, never resent. */
    @Transactional
    public Optional<ReversalRequest> applyTimeout(String originatorConversationId, String conversationId,
                                                  String desc) {
        return lock(originatorConversationId, conversationId, null).map(reversal -> {
            if (reversal.getStatus() == ReversalStatus.SENT || reversal.getStatus() == ReversalStatus.SENDING) {
                reversal.setStatus(ReversalStatus.OUTCOME_UNKNOWN);
                reversal.setTimedOutAt(Instant.now());
                reversal.setResultDesc(clip(desc == null ? "Safaricom's queue timed out" : desc, 500));
                approvals.lockByUuid(reversal.getApprovalUuid()).ifPresent(approval -> {
                    approval.setOutcomeMessage("Safaricom's queue timed out, so the outcome is unknown. Check the "
                            + "transaction before raising the reversal again.");
                    approvals.save(approval);
                });
                return reversals.save(reversal);
            }
            return reversal;
        });
    }

    private Optional<ReversalRequest> lock(String originatorId, String conversationId, String originalReceipt) {
        Optional<ReversalRequest> found = originatorId == null ? Optional.empty()
                : reversals.lockByOriginatorConversationId(originatorId);
        if (found.isEmpty() && conversationId != null) {
            found = reversals.lockByConversationId(conversationId);
        }
        if (found.isEmpty() && originalReceipt != null) {
            List<ReversalRequest> open = reversals.lockOpenByTransactionId(originalReceipt, OPEN);
            found = open.size() == 1 ? Optional.of(open.get(0)) : Optional.empty();
        }
        return found;
    }

    /** Daraja sends ResultParameter as a list of {Key, Value}, or a single object when there is one. */
    static Map<String, String> parameters(JsonNode node) {
        Map<String, String> values = new HashMap<>();
        if (node.isArray()) {
            node.forEach(item -> put(values, item));
        } else if (node.isObject()) {
            put(values, node);
        }
        return values;
    }

    private static void put(Map<String, String> values, JsonNode item) {
        if (item.hasNonNull("Key") && item.has("Value") && !item.get("Value").isNull()) {
            values.put(item.get("Key").asText(), item.get("Value").asText());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
