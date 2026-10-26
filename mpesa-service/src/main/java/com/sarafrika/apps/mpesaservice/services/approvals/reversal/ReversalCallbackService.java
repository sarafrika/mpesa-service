package com.sarafrika.apps.mpesaservice.services.approvals.reversal;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.ReversalRequest;
import com.sarafrika.apps.mpesaservice.repositories.ApprovalRequestRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReversalRequestRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackOutcome;
import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReversalStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Settles reversals from Safaricom's result and timeout callbacks. Result codes per
 * developer.safaricom.co.ke/apis/Reversal: "0" reversed; "R000001" the transaction was already reversed
 * (so the payment counts as reversed); anything else failed. ResultCode may be a number or a string. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReversalCallbackService {

    static final String ALREADY_REVERSED = "R000001";
    static final String AUDIT_CLOSED = "REVERSAL_CLOSED";
    static final String NO_MATCH = "No reversal matches this result. Replay it later, or attach it to its reversal "
            + "under Inbox after checking the statement.";
    static final List<ReversalStatus> OPEN = List.of(ReversalStatus.SENDING, ReversalStatus.SENT,
            ReversalStatus.OUTCOME_UNKNOWN);

    private final ReversalRequestRepository reversals;
    private final ApprovalRequestRepository approvals;
    private final MpesaIncomingPaymentRepository payments;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /** body is the callback as received ({"Result": {...}}); the row is locked, so repeats settle it once.
     * Daraja's refusals (e.g. R000002) carry no ResultParameters, so they match by conversation ids alone. */
    @Transactional
    public Optional<ReversalRequest> applyResult(JsonNode body, String raw) {
        JsonNode result = body.has("Result") ? body.get("Result") : body;
        Map<String, String> params = parameters(result.path("ResultParameters").path("ResultParameter"));
        Optional<ReversalRequest> match = lock(text(result, "OriginatorConversationID"),
                text(result, "ConversationID"), params.get("OriginalTransactionID"));
        if (match.isEmpty()) {
            log.warn("Reversal result for unknown request {} / {}", text(result, "OriginatorConversationID"),
                    text(result, "ConversationID"));
            CallbackOutcome.failed(NO_MATCH);
            return Optional.empty();
        }
        return Optional.of(settle(match.get(), result, raw));
    }

    /** Finance attaches a stored result nothing matched to the reversal it belongs to, after checking. */
    @Transactional
    public ReversalRequest attachResult(UUID reversalUuid, JsonNode body, String raw) {
        JsonNode result = body.has("Result") ? body.get("Result") : body;
        ReversalRequest reversal = reversals.lockByUuid(reversalUuid)
                .orElseThrow(() -> new NotFoundException("Reversal not found"));
        if (!OPEN.contains(reversal.getStatus()) && !closedByHand(reversal)) {
            throw new ConflictException("This reversal is already " + reversal.getStatus() + "; nothing to attach.");
        }
        if (differs(reversal.getOriginatorConversationId(), text(result, "OriginatorConversationID"))
                || differs(reversal.getConversationId(), text(result, "ConversationID"))) {
            throw new ConflictException("The result names another conversation than this reversal.");
        }
        String original = parameters(result.path("ResultParameters").path("ResultParameter"))
                .get("OriginalTransactionID");
        if (original != null && !original.equals(reversal.getTransactionId())) {
            throw new ConflictException("The result reverses " + original + ", not " + reversal.getTransactionId()
                    + ".");
        }
        return settle(reversal, result, raw);
    }

    /** Whether a result (the "Result" object) names this reversal's receipt or amount; a refusal names neither. */
    public static boolean fits(ReversalRequest reversal, JsonNode result) {
        Map<String, String> params = parameters(result.path("ResultParameters").path("ResultParameter"));
        String original = params.get("OriginalTransactionID");
        if (original != null) {
            return original.equals(reversal.getTransactionId());
        }
        String amount = params.get("Amount");
        try {
            return amount != null && new java.math.BigDecimal(amount.trim()).compareTo(reversal.getAmount()) == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** Open reversals newest first, for attaching a stored result by hand. */
    @Transactional(readOnly = true)
    public List<ReversalRequest> attachable() {
        return reversals.findTop50ByStatusInOrderByCreatedAtDescIdDesc(OPEN);
    }

    /** Finance closes a reversal whose outcome stayed unknown as FAILED once the statement shows it did not
     * happen; that frees the payment for a new reversal request (which again needs a checker). */
    @Transactional
    public ReversalRequest closeAsFailed(UUID reversalUuid, String note) {
        if (!StringUtils.hasText(note)) {
            throw new InvalidRequestException("note", "is required: say what the statement showed");
        }
        ReversalRequest reversal = reversals.lockByUuid(reversalUuid)
                .orElseThrow(() -> new NotFoundException("Reversal not found"));
        if (reversal.getStatus() != ReversalStatus.OUTCOME_UNKNOWN) {
            throw new ConflictException("Only a reversal whose outcome is unknown can be closed by hand; this one is "
                    + reversal.getStatus() + ".");
        }
        String actor = CurrentActor.name();
        String trimmed = clip(note.trim(), 500);
        reversal.setStatus(ReversalStatus.FAILED);
        reversal.setClosedBy(clip(actor, 100));
        reversal.setCloseNote(trimmed);
        reversal.setResultDesc(clip("Closed as failed by " + actor + " after checking the statement: " + trimmed,
                500));
        reversal.setCompletedAt(Instant.now());
        ReversalRequest saved = reversals.save(reversal);
        String message = "Closed as failed by " + actor + " after checking the statement: " + trimmed;
        approvals.lockByUuid(saved.getApprovalUuid()).ifPresent(approval -> {
            approval.setOutcomeMessage(clip(message, 1000));
            if (approval.getStatus() == ApprovalStatus.EXECUTED) {
                approval.setStatus(ApprovalStatus.FAILED);
            }
            approvals.save(approval);
        });
        audit.record(AUDIT_CLOSED, "REVERSAL", saved.getUuid().toString(),
                Map.of("status", ReversalStatus.OUTCOME_UNKNOWN.name()),
                Map.of("status", saved.getStatus().name(), "note", trimmed));
        publish(saved);
        return saved;
    }

    private ReversalRequest settle(ReversalRequest reversal, JsonNode result, String raw) {
        String code = text(result, "ResultCode");
        String desc = text(result, "ResultDesc");
        boolean refusedAtSubmit = reversal.getStatus() == ReversalStatus.FAILED && reversal.getRawCallback() == null;
        boolean success = "0".equals(code);
        boolean takes = OPEN.contains(reversal.getStatus()) || closedByHand(reversal)
                || (refusedAtSubmit && (success || ALREADY_REVERSED.equals(code)));
        if (!takes) {
            log.info("Ignoring repeat reversal result for {} already {}", reversal.getUuid(), reversal.getStatus());
            return reversal;
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
            if (success && approval.getStatus() == ApprovalStatus.FAILED) {
                approval.setStatus(ApprovalStatus.EXECUTED);
            }
            approvals.save(approval);
        });
        publish(saved);
        return saved;
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

    // reversal.completed or reversal.failed, to the product that owns the original payment (webhooks).
    private void publish(ReversalRequest reversal) {
        if (reversal.getStatus() != ReversalStatus.REVERSED && reversal.getStatus() != ReversalStatus.FAILED) {
            return;
        }
        Optional<MpesaIncomingPayment> payment = payments.findByUuid(reversal.getPaymentUuid());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reversal_uuid", reversal.getUuid());
        data.put("transaction_id", reversal.getTransactionId());
        data.put("reversal_transaction_id", reversal.getReversalTransactionId());
        data.put("amount", reversal.getAmount());
        data.put("original_amount", reversal.getOriginalAmount());
        data.put("status", reversal.getStatus().name());
        data.put("result_code", reversal.getResultCode());
        data.put("result_desc", reversal.getResultDesc());
        data.put("account_reference", payment.map(MpesaIncomingPayment::getAccountReference).orElse(null));
        String type = reversal.getStatus() == ReversalStatus.REVERSED ? PaymentEvent.REVERSAL_COMPLETED
                : PaymentEvent.REVERSAL_FAILED;
        events.publishEvent(new PaymentEvent(type, reversal.getShortcodeUuid(), reversal.getPaymentUuid(),
                payment.map(MpesaIncomingPayment::getClientId).orElse(null), data));
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

    private static boolean closedByHand(ReversalRequest reversal) {
        return reversal.getStatus() == ReversalStatus.FAILED && reversal.getClosedBy() != null
                && reversal.getRawCallback() == null;
    }

    private static boolean differs(String ours, String theirs) {
        return StringUtils.hasText(ours) && StringUtils.hasText(theirs) && !ours.equals(theirs);
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
