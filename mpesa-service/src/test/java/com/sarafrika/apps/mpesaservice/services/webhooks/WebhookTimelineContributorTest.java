package com.sarafrika.apps.mpesaservice.services.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TimelineEventDto;
import com.sarafrika.apps.mpesaservice.models.WebhookDelivery;
import com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Each delivery becomes one timeline step naming the event, the product, the outcome and the latency. */
class WebhookTimelineContributorTest {

    @Test
    void aDeliveredWebhookShowsItsStatusCodeAndLatency() {
        WebhookDelivery delivery = delivery(WebhookDeliveryStatus.DELIVERED, 1);
        delivery.setResponseCode(200);
        delivery.setLatencyMs(312L);
        delivery.setDeliveredAt(Instant.parse("2026-10-27T09:00:00Z"));

        TimelineEventDto step = WebhookTimelineContributor.event(delivery);

        assertThat(step.title()).isEqualTo("Webhook c2b.confirmed to elimika-backend: delivered");
        assertThat(step.description()).isEqualTo("HTTP 200 in 312 ms · attempt 1");
        assertThat(step.tone()).isEqualTo("success");
        assertThat(step.at().toInstant()).isEqualTo(Instant.parse("2026-10-27T09:00:00Z"));
    }

    @Test
    void retriesAndFailuresCarryTheError() {
        WebhookDelivery retrying = delivery(WebhookDeliveryStatus.PENDING, 2);
        retrying.setResponseCode(500);
        retrying.setError("Server error");
        WebhookDelivery failed = delivery(WebhookDeliveryStatus.FAILED, 5);
        failed.setError("Connection refused");

        assertThat(WebhookTimelineContributor.event(retrying).tone()).isEqualTo("warning");
        assertThat(WebhookTimelineContributor.event(retrying).description())
                .isEqualTo("HTTP 500 · attempt 2 · Server error");
        assertThat(WebhookTimelineContributor.event(failed).tone()).isEqualTo("danger");
        assertThat(WebhookTimelineContributor.event(failed).description()).isEqualTo("attempt 5 · Connection refused");
    }

    private static WebhookDelivery delivery(WebhookDeliveryStatus status, int attempt) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setClientId("elimika-backend");
        delivery.setEventType("c2b.confirmed");
        delivery.setStatus(status);
        delivery.setAttempt(attempt);
        delivery.setUpdatedAt(Instant.parse("2026-10-27T08:59:00Z"));
        return delivery;
    }
}
