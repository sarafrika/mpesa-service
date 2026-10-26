package com.sarafrika.apps.mpesaservice.services.webhooks;

import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TimelineEventDto;
import com.sarafrika.apps.mpesaservice.models.WebhookDelivery;
import com.sarafrika.apps.mpesaservice.repositories.WebhookDeliveryRepository;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionTimelineContributor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Shows each webhook sent about a payment on its timeline: event, product, status and latency. */
@Component
@RequiredArgsConstructor
public class WebhookTimelineContributor implements TransactionTimelineContributor {

    static final String KIND = "webhook";

    private final WebhookDeliveryRepository deliveries;

    @Override
    @Transactional(readOnly = true)
    public List<TimelineEventDto> eventsFor(UUID paymentUuid) {
        return deliveries.findByPaymentUuidOrderByCreatedAtAscIdAsc(paymentUuid).stream()
                .map(WebhookTimelineContributor::event)
                .toList();
    }

    static TimelineEventDto event(WebhookDelivery delivery) {
        String target = delivery.getEventType() + " to " + delivery.getClientId();
        return switch (delivery.getStatus()) {
            case DELIVERED -> new TimelineEventDto(KIND, "Webhook " + target + ": delivered",
                    outcome(delivery), at(delivery.getDeliveredAt() != null ? delivery.getDeliveredAt()
                            : delivery.getUpdatedAt()), "success");
            case FAILED -> new TimelineEventDto(KIND, "Webhook " + target + ": failed",
                    failure(delivery), at(delivery.getUpdatedAt()), "danger");
            case PENDING -> delivery.getAttempt() == 0
                    ? new TimelineEventDto(KIND, "Webhook " + target + ": queued", null,
                            at(delivery.getCreatedAt()), "info")
                    : new TimelineEventDto(KIND, "Webhook " + target + ": retrying", failure(delivery),
                            at(delivery.getUpdatedAt()), "warning");
        };
    }

    private static String outcome(WebhookDelivery delivery) {
        StringBuilder text = new StringBuilder();
        if (delivery.getResponseCode() != null) {
            text.append("HTTP ").append(delivery.getResponseCode());
        }
        if (delivery.getLatencyMs() != null) {
            text.append(text.isEmpty() ? "" : " in ").append(delivery.getLatencyMs()).append(" ms");
        }
        text.append(text.isEmpty() ? "" : " · ").append("attempt ").append(delivery.getAttempt());
        return text.toString();
    }

    private static String failure(WebhookDelivery delivery) {
        String outcome = outcome(delivery);
        return delivery.getError() == null || delivery.getError().isBlank() ? outcome
                : outcome + " · " + delivery.getError();
    }

    private static OffsetDateTime at(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
