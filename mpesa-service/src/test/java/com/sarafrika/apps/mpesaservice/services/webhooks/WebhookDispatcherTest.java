package com.sarafrika.apps.mpesaservice.services.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.models.WebhookDelivery;
import com.sarafrika.apps.mpesaservice.models.WebhookSubscription;
import com.sarafrika.apps.mpesaservice.repositories.WebhookDeliveryRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookSubscriptionRepository;
import com.sarafrika.apps.mpesaservice.services.security.TestUrlGuards;
import com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class WebhookDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    private static final String SECRET = "whsec_dispatch_test";

    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final WebhookSubscriptionRepository subscriptions = mock(WebhookSubscriptionRepository.class);
    private final WebhookTransport transport = mock(WebhookTransport.class);
    private final WebhookDispatcher dispatcher = new WebhookDispatcher(deliveries, subscriptions, transport,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), TestUrlGuards.publicDns(), true, 20,
            Duration.ofMinutes(2));
    private WebhookSubscription subscription;
    private WebhookDelivery delivery;

    @BeforeEach
    void setUp() {
        dispatcher.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        subscription = new WebhookSubscription();
        subscription.setUuid(UUID.randomUUID());
        subscription.setClientId("elimika-backend");
        subscription.setUrl("https://elimika.test/hooks/mpesa");
        subscription.setSecret(SECRET);
        delivery = new WebhookDelivery();
        delivery.setId(7L);
        delivery.setUuid(UUID.randomUUID());
        delivery.setSubscriptionUuid(subscription.getUuid());
        delivery.setEventType("c2b.confirmed");
        delivery.setPayload("{\"type\":\"c2b.confirmed\"}");
        when(subscriptions.findByUuid(subscription.getUuid())).thenReturn(Optional.of(subscription));
        when(deliveries.findById(7L)).thenReturn(Optional.of(delivery));
    }

    @Test
    @SuppressWarnings("unchecked")
    void a2xxIsDeliveredWithSignedHeaders() throws Exception {
        when(transport.post(any(), anyMap(), anyString())).thenReturn(200);

        dispatcher.attempt(delivery);

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(transport).post(any(URI.class), headers.capture(), anyString());
        assertThat(headers.getValue()).containsEntry("X-Sarafrika-Event", "c2b.confirmed")
                .containsEntry("X-Sarafrika-Delivery", delivery.getUuid().toString())
                .containsEntry("Content-Type", "application/json");
        String signature = headers.getValue().get("X-Sarafrika-Signature");
        assertThat(signature).startsWith("t=" + NOW.getEpochSecond() + ",v1=");
        assertThat(WebhookSigner.verify(SECRET, signature, delivery.getPayload())).isTrue();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getAttempt()).isEqualTo(1);
        assertThat(delivery.getResponseCode()).isEqualTo(200);
        assertThat(delivery.getLatencyMs()).isNotNull();
        assertThat(delivery.getDeliveredAt()).isEqualTo(NOW);
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void failuresFollowTheRetryScheduleThenFail() throws Exception {
        when(transport.post(any(), anyMap(), anyString())).thenReturn(500);
        Duration[] expected = {Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(10),
                Duration.ofHours(1)};

        for (Duration wait : expected) {
            dispatcher.attempt(delivery);
            assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
            assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plus(wait));
            assertThat(delivery.getError()).contains("HTTP 500");
        }
        dispatcher.attempt(delivery);

        assertThat(delivery.getAttempt()).isEqualTo(5);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void aTimeoutIsRetried() throws Exception {
        when(transport.post(any(), anyMap(), anyString())).thenThrow(new HttpTimeoutException("timed out"));

        dispatcher.attempt(delivery);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getResponseCode()).isNull();
        assertThat(delivery.getError()).isEqualTo("Timed out waiting for the receiver");
    }

    @Test
    void aUrlNowPointingInsideIsRefusedAtSendTime() throws Exception {
        subscription.setUrl("https://10.0.0.7/hooks");

        dispatcher.attempt(delivery);

        verify(transport, never()).post(any(), anyMap(), anyString());
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getError()).contains("not allowed");
    }

    @Test
    void anAttemptThatLostItsClaimDoesNotOverwriteTheNewOne() throws Exception {
        when(transport.post(any(), anyMap(), anyString())).thenReturn(200);
        WebhookDelivery stale = new WebhookDelivery();
        stale.setId(7L);
        stale.setUuid(delivery.getUuid());
        stale.setSubscriptionUuid(subscription.getUuid());
        stale.setEventType("c2b.confirmed");
        stale.setPayload(delivery.getPayload());
        stale.setClaimToken(UUID.randomUUID());
        delivery.setClaimToken(UUID.randomUUID());

        dispatcher.attempt(stale);

        assertThat(delivery.getAttempt()).isZero();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
    }

    @Test
    void aRemovedOrDisabledSubscriptionFailsWithoutSending() throws Exception {
        subscription.setActive(false);

        dispatcher.attempt(delivery);

        verify(transport, never()).post(any(), anyMap(), anyString());
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getError()).contains("removed or disabled");
    }
}
