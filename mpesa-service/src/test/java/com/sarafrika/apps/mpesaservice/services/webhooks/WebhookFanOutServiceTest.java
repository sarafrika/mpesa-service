package com.sarafrika.apps.mpesaservice.services.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.models.WebhookDelivery;
import com.sarafrika.apps.mpesaservice.models.WebhookSubscription;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookDeliveryRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookSubscriptionRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Who receives which event: c2b.* by C2B_FEED, stk.* by STK_PUSH (and the starter), b2c.* the payer only. */
class WebhookFanOutServiceTest {

    private static final UUID PAYBILL = UUID.randomUUID();

    private final ClientShortcodeGrantRepository grants = mock(ClientShortcodeGrantRepository.class);
    private final WebhookSubscriptionRepository subscriptions = mock(WebhookSubscriptionRepository.class);
    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final WebhookFanOutService fanOut = new WebhookFanOutService(grants, subscriptions, deliveries, objectMapper);

    @BeforeEach
    void setUp() {
        when(grants.findByShortcodeUuidAndActiveTrue(PAYBILL)).thenReturn(List.of(
                grant("elimika-backend", GrantOperation.STK_PUSH, GrantOperation.C2B_FEED),
                grant("shop-api", GrantOperation.STK_PUSH),
                grant("reports", GrantOperation.STATUS_LOOKUP)));
        when(subscriptions.findByClientIdInAndActiveTrue(anyCollection())).thenAnswer(call -> {
            Collection<String> clients = call.getArgument(0);
            return List.of(subscription("elimika-backend", ""), subscription("shop-api", ""),
                            subscription("reports", ""), subscription("payouts-app", "b2c.completed"))
                    .stream().filter(s -> clients.contains(s.getClientId())).toList();
        });
        when(deliveries.saveAll(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void c2bGoesToEveryProductWithTheC2bFeed() {
        assertThat(clients(fanOut.enqueue(event(PaymentEvent.C2B_CONFIRMED, null)))).containsExactly("elimika-backend");
    }

    @Test
    void stkGoesToTheProductThatStartedThePushWhenNamed() {
        assertThat(clients(fanOut.enqueue(event(PaymentEvent.STK_COMPLETED, "shop-api")))).containsExactly("shop-api");
        assertThat(fanOut.recipients(event(PaymentEvent.STK_FAILED, null)))
                .containsExactlyInAnyOrder("elimika-backend", "shop-api");
        assertThat(fanOut.recipients(event(PaymentEvent.STK_CANCELLED, "reports"))).isEmpty();
    }

    @Test
    void payoutsGoOnlyToThePayingClientAndRespectTheEventFilter() {
        assertThat(clients(fanOut.enqueue(event(PaymentEvent.B2C_COMPLETED, "payouts-app"))))
                .containsExactly("payouts-app");
        assertThat(fanOut.enqueue(event(PaymentEvent.B2C_FAILED, "payouts-app"))).isEmpty();
        assertThat(fanOut.recipients(event(PaymentEvent.B2C_COMPLETED, null))).isEmpty();
    }

    @Test
    void aReversalGoesWhereItsPaymentWent() {
        // An STK payment's product, while it still has the grant; a C2B payment's C2B feed products.
        assertThat(fanOut.recipients(event(PaymentEvent.REVERSAL_COMPLETED, "shop-api"))).containsExactly("shop-api");
        assertThat(fanOut.recipients(event(PaymentEvent.REVERSAL_FAILED, "reports"))).isEmpty();
        assertThat(fanOut.recipients(event(PaymentEvent.REVERSAL_FAILED, null))).containsExactly("elimika-backend");
        assertThat(WebhookEvents.SUBSCRIBABLE).contains("reversal.completed", "reversal.failed");
    }

    @Test
    void unknownEventsGoNowhere() {
        assertThat(fanOut.enqueue(event("balance.updated", "elimika-backend"))).isEmpty();
        verify(deliveries, never()).saveAll(any());
    }

    @Test
    void theBodyCarriesTheEventIdTypeAndDataAndTheReferenceIsTheReceipt() throws Exception {
        WebhookDelivery delivery = fanOut.enqueue(event(PaymentEvent.C2B_CONFIRMED, null)).get(0);

        JsonNode body = objectMapper.readTree(delivery.getPayload());
        assertThat(body.path("id").asText()).isEqualTo(delivery.getEventId().toString());
        assertThat(body.path("type").asText()).isEqualTo("c2b.confirmed");
        assertThat(body.path("shortcode_uuid").asText()).isEqualTo(PAYBILL.toString());
        assertThat(body.path("data").path("transaction_id").asText()).isEqualTo("TJQ4K2L9XA");
        assertThat(delivery.getReference()).isEqualTo("TJQ4K2L9XA");
        assertThat(delivery.getNextAttemptAt()).isNotNull();
    }

    @Test
    void theListenerQueuesThenKicksAndNeverThrows() {
        WebhookFanOutService service = mock(WebhookFanOutService.class);
        WebhookDispatcher dispatcher = mock(WebhookDispatcher.class);
        PaymentEventWebhookListener listener = new PaymentEventWebhookListener(service, dispatcher);
        PaymentEvent event = event(PaymentEvent.C2B_CONFIRMED, null);

        when(service.enqueue(event)).thenReturn(List.of(new WebhookDelivery()));
        listener.onPaymentEvent(event);
        verify(dispatcher).kick();

        PaymentEvent other = event(PaymentEvent.STK_FAILED, null);
        doThrow(new IllegalStateException("db down")).when(service).enqueue(other);
        listener.onPaymentEvent(other);
    }

    private static List<String> clients(List<WebhookDelivery> queued) {
        return queued.stream().map(WebhookDelivery::getClientId).toList();
    }

    private static PaymentEvent event(String type, String clientId) {
        return new PaymentEvent(type, PAYBILL, UUID.randomUUID(), clientId,
                Map.of("transaction_id", "TJQ4K2L9XA", "amount", 1500));
    }

    private static ClientShortcodeGrant grant(String client, GrantOperation... ops) {
        ClientShortcodeGrant grant = new ClientShortcodeGrant();
        grant.setClientId(client);
        grant.setShortcodeUuid(PAYBILL);
        grant.setOperations(EnumSet.of(ops[0], ops));
        return grant;
    }

    private static WebhookSubscription subscription(String client, String types) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setUuid(UUID.randomUUID());
        subscription.setClientId(client);
        subscription.setUrl("https://" + client + ".test/hooks");
        subscription.setEventTypeSet(types.isEmpty() ? Set.of() : Set.of(types.split(",")));
        return subscription;
    }
}
