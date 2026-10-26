package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.TransactionStatusQuery;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaOutgoingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.TransactionStatusQueryRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The ids in a callback body (receipts, checkout and conversation ids) and the payment they belong to. */
@Component
@RequiredArgsConstructor
public class CallbackReferences {

    static final int MAX_LENGTH = 500;
    // Daraja's id fields across C2B, STK, B2C/B2B, status, balance and reversal callbacks.
    static final Set<String> ID_KEYS = Set.of("TransID", "CheckoutRequestID", "MerchantRequestID", "ConversationID",
            "OriginatorConversationID", "TransactionID", "BillRefNumber", "MpesaReceiptNumber", "TransactionReceipt",
            "ReceiptNo");

    private final MpesaIncomingPaymentRepository incoming;
    private final MpesaOutgoingPaymentRepository outgoing;
    private final TransactionStatusQueryRepository statusQueries;

    /** Every id value, first occurrence order, keyed by field name (the first value of each name wins). */
    static Map<String, String> ids(JsonNode body) {
        Map<String, String> found = new LinkedHashMap<>();
        collect(body, found);
        return found;
    }

    static String searchText(JsonNode body) {
        String joined = String.join(" ", ids(body).values());
        return joined.isEmpty() ? null : joined.length() > MAX_LENGTH ? joined.substring(0, MAX_LENGTH) : joined;
    }

    private static void collect(JsonNode node, Map<String, String> found) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collect(child, found));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        // Daraja's {"Name"|"Key": "MpesaReceiptNumber", "Value": "..."} items.
        JsonNode label = node.hasNonNull("Name") ? node.get("Name") : node.get("Key");
        if (label != null && label.isTextual() && ID_KEYS.contains(label.asText()) && node.hasNonNull("Value")
                && node.get("Value").isValueNode()) {
            put(found, label.asText(), node.get("Value").asText());
        }
        node.properties().forEach(field -> {
            JsonNode value = field.getValue();
            if (ID_KEYS.contains(field.getKey()) && value.isValueNode() && !value.isNull()) {
                put(found, field.getKey(), value.asText());
            } else {
                collect(value, found);
            }
        });
    }

    private static void put(Map<String, String> found, String key, String value) {
        String trimmed = value.trim();
        if (!trimmed.isEmpty() && !found.containsValue(trimmed)) {
            found.putIfAbsent(key, trimmed);
        }
    }

    /** The payment (incoming or outgoing uuid, as the transactions page shows it) the callback is about. */
    @Transactional(readOnly = true)
    public Optional<UUID> paymentFor(CallbackKind kind, JsonNode body) {
        if (body == null || kind == CallbackKind.C2B_VALIDATION) {
            return Optional.empty();
        }
        Map<String, String> ids = ids(body);
        return switch (kind) {
            case C2B_CONFIRMATION -> first(ids, List.of("TransID"))
                    .flatMap(incoming::findByTransactionId).map(MpesaIncomingPayment::getUuid);
            case STK_CALLBACK -> first(ids, List.of("CheckoutRequestID"))
                    .flatMap(incoming::findByCheckoutRequestId).map(MpesaIncomingPayment::getUuid);
            case TRANSACTION_STATUS_RESULT, TRANSACTION_STATUS_TIMEOUT -> statusQuery(ids);
            default -> payout(ids);
        };
    }

    private Optional<UUID> payout(Map<String, String> ids) {
        Optional<UUID> byOriginator = Optional.ofNullable(ids.get("OriginatorConversationID"))
                .flatMap(outgoing::findByOriginatorConversationId).map(MpesaOutgoingPayment::getUuid);
        return byOriginator.isPresent() ? byOriginator : Optional.ofNullable(ids.get("ConversationID"))
                .flatMap(outgoing::findByConversationId).map(MpesaOutgoingPayment::getUuid);
    }

    private Optional<UUID> statusQuery(Map<String, String> ids) {
        Optional<TransactionStatusQuery> query = Optional.ofNullable(ids.get("ConversationID"))
                .flatMap(statusQueries::findFirstByConversationIdOrderByCreatedAtDesc);
        if (query.isEmpty()) {
            query = Optional.ofNullable(ids.get("OriginatorConversationID"))
                    .flatMap(statusQueries::findFirstByOriginatorConversationIdOrderByCreatedAtDesc);
        }
        return query.map(TransactionStatusQuery::getPaymentUuid);
    }

    private static Optional<String> first(Map<String, String> ids, List<String> keys) {
        return keys.stream().map(ids::get).filter(v -> v != null).findFirst();
    }
}
