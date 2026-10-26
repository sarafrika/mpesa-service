package com.sarafrika.apps.mpesaservice.services.webhooks;

import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.CreateWebhookSubscriptionRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.UpdateWebhookSubscriptionRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookDeliveryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookSecretResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookSubscriptionDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.models.WebhookDelivery;
import com.sarafrika.apps.mpesaservice.models.WebhookSubscription;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookDeliveryRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookSubscriptionRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** Console management of webhook subscriptions and their deliveries; every change is audited. */
@Service
@RequiredArgsConstructor
public class WebhookAdminService {

    static final String ENTITY_SUBSCRIPTION = "WEBHOOK_SUBSCRIPTION";
    static final String ENTITY_DELIVERY = "WEBHOOK_DELIVERY";
    private static final Duration HEALTH_WINDOW = Duration.ofDays(7);

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final ClientShortcodeGrantRepository grants;
    private final WebhookFanOutService fanOut;
    private final WebhookDispatcher dispatcher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /** Optional exact-match filters for the delivery list; blank values are ignored. */
    public record DeliveryQuery(String clientId, UUID subscriptionUuid, WebhookDeliveryStatus status,
                                String eventType) {
    }

    @Transactional(readOnly = true)
    public Page<WebhookSubscriptionDto> listSubscriptions(String clientId, Pageable pageable) {
        Page<WebhookSubscription> page = StringUtils.hasText(clientId)
                ? subscriptions.findByClientId(clientId.trim(), pageable) : subscriptions.findAll(pageable);
        Map<UUID, WebhookHealthDto> health = health(page.getContent());
        return page.map(subscription -> toDto(subscription, health));
    }

    @Transactional(readOnly = true)
    public WebhookSubscriptionDto getSubscription(UUID uuid) {
        WebhookSubscription subscription = find(uuid);
        return toDto(subscription, health(List.of(subscription)));
    }

    @Transactional
    public WebhookSecretResponse create(CreateWebhookSubscriptionRequest request) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setClientId(request.clientId().trim());
        subscription.setUrl(WebhookUrls.validated("url", request.url()));
        subscription.setEventTypeSet(WebhookEvents.validated(request.eventTypes()));
        String secret = WebhookSigner.newSecret();
        subscription.setSecret(secret);
        subscription.setCreatedBy(CurrentActor.name());
        WebhookSubscription saved = subscriptions.save(subscription);
        WebhookSubscriptionDto dto = toDto(saved, Map.of());
        auditService.record("WEBHOOK_SUBSCRIPTION_CREATED", ENTITY_SUBSCRIPTION, saved.getUuid().toString(), null, dto);
        return new WebhookSecretResponse(dto, secret);
    }

    @Transactional
    public WebhookSubscriptionDto update(UUID uuid, UpdateWebhookSubscriptionRequest request) {
        WebhookSubscription subscription = find(uuid);
        WebhookSubscriptionDto before = toDto(subscription, Map.of());
        if (request.url() != null) {
            subscription.setUrl(WebhookUrls.validated("url", request.url()));
        }
        if (request.eventTypes() != null) {
            subscription.setEventTypeSet(WebhookEvents.validated(request.eventTypes()));
        }
        if (request.active() != null) {
            subscription.setActive(request.active());
        }
        subscription.setUpdatedBy(CurrentActor.name());
        WebhookSubscription saved = subscriptions.save(subscription);
        WebhookSubscriptionDto after = toDto(saved, health(List.of(saved)));
        auditService.record("WEBHOOK_SUBSCRIPTION_UPDATED", ENTITY_SUBSCRIPTION, uuid.toString(), before, after);
        return after;
    }

    @Transactional
    public void delete(UUID uuid) {
        WebhookSubscription subscription = find(uuid);
        WebhookSubscriptionDto before = toDto(subscription, Map.of());
        subscriptions.delete(subscription);
        auditService.record("WEBHOOK_SUBSCRIPTION_DELETED", ENTITY_SUBSCRIPTION, uuid.toString(), before, null);
    }

    /** Replaces the signing secret at once; the old one stops working for the next attempt. */
    @Transactional
    public WebhookSecretResponse rotateSecret(UUID uuid) {
        WebhookSubscription subscription = find(uuid);
        String secret = WebhookSigner.newSecret();
        subscription.setSecret(secret);
        subscription.setSecretRotatedAt(LocalDateTime.now());
        subscription.setUpdatedBy(CurrentActor.name());
        WebhookSubscription saved = subscriptions.save(subscription);
        WebhookSubscriptionDto dto = toDto(saved, health(List.of(saved)));
        auditService.record("WEBHOOK_SECRET_ROTATED", ENTITY_SUBSCRIPTION, uuid.toString(), null,
                Map.of("subscription_uuid", uuid, "client_id", saved.getClientId()));
        return new WebhookSecretResponse(dto, secret);
    }

    @Transactional
    public WebhookDeliveryDto sendTest(UUID uuid) {
        WebhookSubscription subscription = find(uuid);
        if (!subscription.isActive()) {
            throw new ConflictException("This webhook is disabled; enable it before sending a test event");
        }
        WebhookDelivery delivery = fanOut.enqueueTest(subscription);
        auditService.record("WEBHOOK_TEST_SENT", ENTITY_DELIVERY, delivery.getUuid().toString(), null,
                Map.of("subscription_uuid", uuid, "event_type", WebhookEvents.TEST));
        kickAfterCommit();
        return toDto(delivery, names(Set.of(delivery.getClientId())), false);
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryDto> listDeliveries(DeliveryQuery query, Pageable pageable) {
        Page<WebhookDelivery> page = deliveries.findAll(matching(query), pageable);
        Map<String, String> names = names(page.getContent().stream().map(WebhookDelivery::getClientId)
                .collect(Collectors.toSet()));
        return page.map(delivery -> toDto(delivery, names, false));
    }

    @Transactional(readOnly = true)
    public WebhookDeliveryDto getDelivery(UUID uuid) {
        WebhookDelivery delivery = findDelivery(uuid);
        return toDto(delivery, names(Set.of(delivery.getClientId())), true);
    }

    /** Sends the same delivery again (same id, so receivers can de-duplicate), with a fresh set of retries. */
    @Transactional
    public WebhookDeliveryDto replay(UUID uuid) {
        WebhookDelivery delivery = findDelivery(uuid);
        if (delivery.getStatus() == WebhookDeliveryStatus.PENDING && delivery.getAttempt() == 0) {
            throw new ConflictException("This delivery has not been attempted yet");
        }
        Map<String, Object> before = Map.of("status", delivery.getStatus().name(), "attempt", delivery.getAttempt());
        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setAttempt(0);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setError(null);
        WebhookDelivery saved = deliveries.save(delivery);
        auditService.record("WEBHOOK_DELIVERY_REPLAYED", ENTITY_DELIVERY, uuid.toString(), before,
                Map.of("status", saved.getStatus().name(), "event_type", saved.getEventType()));
        kickAfterCommit();
        return toDto(saved, names(Set.of(saved.getClientId())), false);
    }

    /** 7-day health per subscription; ProductAdminService shows the same figures. */
    @Transactional(readOnly = true)
    public Map<UUID, WebhookHealthDto> health(Collection<WebhookSubscription> subs) {
        if (subs.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Object[]> rows = new HashMap<>();
        for (Object[] row : deliveries.healthSince(Instant.now().minus(HEALTH_WINDOW))) {
            rows.put((UUID) row[0], row);
        }
        Map<UUID, WebhookHealthDto> result = new LinkedHashMap<>();
        for (WebhookSubscription sub : subs) {
            Object[] row = rows.get(sub.getUuid());
            result.put(sub.getUuid(), row == null ? WebhookHealthDto.of(sub.isActive(), 0, 0, 0, 0, null)
                    : WebhookHealthDto.of(sub.isActive(), number(row[1]), number(row[2]), number(row[3]),
                    number(row[4]), (Instant) row[5]));
        }
        return result;
    }

    public WebhookSubscriptionDto toDto(WebhookSubscription subscription, Map<UUID, WebhookHealthDto> health) {
        WebhookHealthDto status = health.getOrDefault(subscription.getUuid(),
                WebhookHealthDto.of(subscription.isActive(), 0, 0, 0, 0, null));
        return new WebhookSubscriptionDto(subscription.getUuid(), subscription.getClientId(), subscription.getUrl(),
                List.copyOf(subscription.eventTypeSet()), subscription.isActive(),
                WebhookSigner.hint(subscription.getSecret()), subscription.getSecretRotatedAt(), status,
                subscription.getCreatedAt(), subscription.getUpdatedAt());
    }

    private WebhookDeliveryDto toDto(WebhookDelivery delivery, Map<String, String> names, boolean withPayload) {
        return new WebhookDeliveryDto(delivery.getUuid(), delivery.getSubscriptionUuid(), delivery.getClientId(),
                names.getOrDefault(delivery.getClientId(), delivery.getClientId()), delivery.getEventId(),
                delivery.getEventType(), delivery.getReference(), delivery.getAttempt(), delivery.getStatus(),
                delivery.getResponseCode(), delivery.getLatencyMs(), delivery.getNextAttemptAt(), delivery.getError(),
                delivery.getDeliveredAt(), delivery.getCreatedAt(), withPayload ? parse(delivery.getPayload()) : null);
    }

    /** Display names from the products' grants, keyed by client id. */
    Map<String, String> names(Set<String> clientIds) {
        if (clientIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        Specification<ClientShortcodeGrant> byClient = (root, q, cb) -> root.get("clientId").in(clientIds);
        for (ClientShortcodeGrant grant : grants.findAll(byClient)) {
            if (StringUtils.hasText(grant.getDisplayName())) {
                names.putIfAbsent(grant.getClientId(), grant.getDisplayName());
            }
        }
        return names;
    }

    private void kickAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.kick();
                }
            });
        } else {
            dispatcher.kick();
        }
    }

    private WebhookSubscription find(UUID uuid) {
        return subscriptions.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Webhook not found"));
    }

    private WebhookDelivery findDelivery(UUID uuid) {
        return deliveries.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Webhook delivery not found"));
    }

    private JsonNode parse(String json) {
        try {
            return json == null ? null : objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    private static Specification<WebhookDelivery> matching(DeliveryQuery query) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.clientId())) {
                predicates.add(cb.equal(root.get("clientId"), query.clientId().trim()));
            }
            if (query.subscriptionUuid() != null) {
                predicates.add(cb.equal(root.get("subscriptionUuid"), query.subscriptionUuid()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (StringUtils.hasText(query.eventType())) {
                predicates.add(cb.equal(root.get("eventType"), query.eventType().trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
