package com.sarafrika.apps.mpesaservice.services.webhooks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.models.WebhookDelivery;
import com.sarafrika.apps.mpesaservice.models.WebhookSubscription;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookDeliveryRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookSubscriptionRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Turns a payment event into one PENDING delivery per subscription whose product is entitled to it. */
@Service
@RequiredArgsConstructor
public class WebhookFanOutService {

    private final ClientShortcodeGrantRepository grants;
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<WebhookDelivery> enqueue(PaymentEvent event) {
        Set<String> clients = recipients(event);
        if (clients.isEmpty()) {
            return List.of();
        }
        UUID eventId = UUID.randomUUID();
        String body = body(eventId, event.type(), event.shortcodeUuid(), event.paymentUuid(), event.data());
        String reference = WebhookEvents.reference(event.data());
        List<WebhookDelivery> queued = subscriptions.findByClientIdInAndActiveTrue(clients).stream()
                .filter(subscription -> subscription.wants(event.type()))
                .map(subscription -> pending(subscription, eventId, event.type(), reference, body,
                        event.paymentUuid()))
                .toList();
        return deliveries.saveAll(queued);
    }

    /** A test.ping to one subscription, whatever its event filter. */
    @Transactional
    public WebhookDelivery enqueueTest(WebhookSubscription subscription) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Test event from the Sarafrika M-Pesa console");
        data.put("client_id", subscription.getClientId());
        String body = body(eventId, WebhookEvents.TEST, null, null, data);
        return deliveries.save(pending(subscription, eventId, WebhookEvents.TEST, "test", body, null));
    }

    /** Client ids entitled to the event on its shortcode. A reversal goes where its payment's own event went:
     * the product that started the STK push, else every product with the C2B feed. */
    Set<String> recipients(PaymentEvent event) {
        if (WebhookEvents.isPayout(event.type())) {
            return event.clientId() == null ? Set.of() : Set.of(event.clientId());
        }
        Optional<GrantOperation> required = WebhookEvents.isReversal(event.type())
                ? Optional.of(event.clientId() != null ? GrantOperation.STK_PUSH : GrantOperation.C2B_FEED)
                : WebhookEvents.requiredGrant(event.type());
        if (required.isEmpty() || event.shortcodeUuid() == null) {
            return Set.of();
        }
        Set<String> entitled = grants.findByShortcodeUuidAndActiveTrue(event.shortcodeUuid()).stream()
                .filter(grant -> grant.allows(required.get()))
                .map(ClientShortcodeGrant::getClientId)
                .collect(Collectors.toSet());
        // An STK result belongs to the product that started the push, when the event names it.
        if (required.get() == GrantOperation.STK_PUSH && event.clientId() != null) {
            return entitled.contains(event.clientId()) ? Set.of(event.clientId()) : Set.of();
        }
        return entitled;
    }

    private String body(UUID eventId, String type, UUID shortcodeUuid, UUID paymentUuid, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", eventId);
        payload.put("type", type);
        payload.put("created_at", Instant.now().toString());
        payload.put("shortcode_uuid", shortcodeUuid);
        payload.put("payment_uuid", paymentUuid);
        payload.put("data", data == null ? Map.of() : data);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise a webhook event", e);
        }
    }

    private static WebhookDelivery pending(WebhookSubscription subscription, UUID eventId, String type,
                                           String reference, String body, UUID paymentUuid) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setSubscriptionUuid(subscription.getUuid());
        delivery.setClientId(subscription.getClientId());
        delivery.setEventId(eventId);
        delivery.setEventType(type);
        delivery.setPaymentUuid(paymentUuid);
        delivery.setReference(reference);
        delivery.setPayload(body);
        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setNextAttemptAt(Instant.now());
        return delivery;
    }
}
