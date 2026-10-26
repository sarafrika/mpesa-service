package com.sarafrika.apps.mpesaservice.services.products;

import com.sarafrika.apps.mpesaservice.dtos.admin.products.UpsertProductGrantRequest;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ConnectProductRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ConnectProductResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ProductDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookHealthDto;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.WebhookDelivery;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookDeliveryRepository;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.impl.MpesaIncomingPaymentServiceImpl;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookAdminService;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookDispatcher;
import com.sarafrika.apps.mpesaservice.services.webhooks.PaymentEventWebhookListener;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookFanOutService;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookTimelineContributor;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookSigner;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookTransport;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Grants, API usage and webhooks on the real migrations: Hibernate validates the schema against them. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "mpesa.webhooks.dispatcher.enabled=false"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductAdminService.class, ApiUsageRecorder.class, WebhookAdminService.class, WebhookFanOutService.class,
        WebhookDispatcher.class, AuditService.class, SecretCipher.class, MpesaIncomingPaymentServiceImpl.class,
        PaymentEventWebhookListener.class, WebhookTimelineContributor.class,
        ProductsAndWebhooksIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductsAndWebhooksIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ProductAdminService products;
    @Autowired
    private ApiUsageRecorder usage;
    @Autowired
    private WebhookFanOutService fanOut;
    @Autowired
    private WebhookDispatcher dispatcher;
    @Autowired
    private WebhookDeliveryRepository deliveries;
    @Autowired
    private MpesaShortCodeRepository shortCodes;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MpesaIncomingPaymentServiceImpl incomingPayments;
    @Autowired
    private WebhookTimelineContributor timeline;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private WebhookAdminService webhooks;

    @MockitoBean
    private WebhookTransport transport;

    private MpesaShortCode paybill;
    private String clientId;

    @TestConfiguration
    static class Beans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of());
        }

        @Bean
        com.sarafrika.apps.mpesaservice.services.security.OutboundUrlGuard outboundUrlGuard() {
            return com.sarafrika.apps.mpesaservice.services.security.TestUrlGuards.publicDns();
        }
    }

    @BeforeEach
    void setUp() {
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(String.valueOf(600000 + (int) (Math.random() * 399999)));
        code.setShortcodeType(ShortcodeType.PAYBILL);
        code.setBusinessName("Elimika Fees");
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setCallbackUrl("https://example.test/api/v1/callbacks/stk/callback");
        code.setEnvironment(Environment.SANDBOX);
        paybill = shortCodes.saveAndFlush(code);
        clientId = "elimika-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void connectingAProductStoresItsGrantsAndAnEncryptedWebhookSecret() {
        ConnectProductResponse connected = connect();

        assertThat(connected.webhookSecret()).startsWith("whsec_");
        assertThat(connected.product().state()).isEqualTo(ProductDto.State.IDLE);
        assertThat(connected.product().grants()).singleElement().satisfies(grant -> {
            assertThat(grant.shortcode()).isEqualTo(paybill.getShortcode());
            assertThat(grant.operations()).containsExactly(GrantOperation.STK_PUSH, GrantOperation.C2B_FEED);
        });
        assertThat(connected.webhook().secretHint()).isEqualTo(WebhookSigner.hint(connected.webhookSecret()));
        String stored = jdbc.queryForObject("SELECT secret FROM webhook_subscriptions WHERE uuid = ?", String.class,
                connected.webhook().uuid());
        assertThat(stored).startsWith(SecretCipher.PREFIX).doesNotContain(connected.webhookSecret());
        assertThat(jdbc.queryForObject("SELECT operations FROM client_shortcode_grants WHERE client_id = ?",
                String.class, clientId)).isEqualTo("STK_PUSH,C2B_FEED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE entity_id = ?", Long.class, clientId))
                .isEqualTo(1);
    }

    @Test
    void deletingAWebhookOrGrantKeepsTheRowsAndHistory() {
        ConnectProductResponse connected = connect();
        UUID subscription = connected.webhook().uuid();
        List<WebhookDelivery> queued = fanOut.enqueue(new PaymentEvent(PaymentEvent.C2B_CONFIRMED, paybill.getUuid(),
                UUID.randomUUID(), null, Map.of("transaction_id", "TJQ4K2L9XD", "amount", 10)));
        assertThat(queued).hasSize(1);

        webhooks.delete(subscription);
        products.deleteGrant(clientId, paybill.getUuid());

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM webhook_subscriptions WHERE uuid = ?",
                Boolean.class, subscription)).isTrue();
        assertThat(deliveries.findByUuid(queued.get(0).getUuid())).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM client_shortcode_grants WHERE client_id = ? "
                + "AND deleted_at IS NOT NULL", Long.class, clientId)).isEqualTo(1);
        assertThatThrownBy(() -> webhooks.getSubscription(subscription)).isInstanceOf(NotFoundException.class);
        assertThat(products.upsertGrant(clientId, paybill.getUuid(), new UpsertProductGrantRequest(
                null, java.util.Set.of(GrantOperation.STATUS_LOOKUP), true)).operations()).containsExactly(GrantOperation.STATUS_LOOKUP);
    }

    @Test
    void apiCallsAreCountedPerDayAndShowOnTheProduct() {
        connect();
        usage.record(clientId, false);
        usage.record(clientId, false);
        usage.record(clientId, true);
        usage.record(clientId, false);

        ProductDto product = products.getProduct(clientId);

        assertThat(product.stats().apiCalls7d()).isEqualTo(4);
        assertThat(product.stats().apiErrors7d()).isEqualTo(1);
        assertThat(product.stats().errorRate7d()).isEqualTo(0.25);
        assertThat(product.state()).isEqualTo(ProductDto.State.LIVE);
        assertThat(products.listProducts(clientId, PageRequest.of(0, 25)).getContent())
                .extracting(ProductDto::clientId).containsExactly(clientId);
    }

    @Test
    void aC2bEventIsQueuedClaimedOnceSignedAndDelivered() throws Exception {
        ConnectProductResponse connected = connect();
        when(transport.post(any(), anyMap(), anyString())).thenReturn(200);

        List<WebhookDelivery> queued = fanOut.enqueue(new PaymentEvent(PaymentEvent.C2B_CONFIRMED, paybill.getUuid(),
                UUID.randomUUID(), null, Map.of("transaction_id", "TJQ4K2L9XA", "amount", 1500)));
        assertThat(queued).hasSize(1);

        assertThat(dispatcher.dispatchDue()).isGreaterThanOrEqualTo(1);
        assertThat(dispatcher.dispatchDue()).isZero();

        WebhookDelivery sent = deliveries.findByUuid(queued.get(0).getUuid()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(sent.getAttempt()).isEqualTo(1);
        assertThat(sent.getReference()).isEqualTo("TJQ4K2L9XA");
        WebhookHealthDto health = products.getProduct(clientId).webhooks().get(0).health();
        assertThat(health.status()).isEqualTo(WebhookHealthDto.Status.HEALTHY);
        assertThat(health.deliveredFirstTry7d()).isEqualTo(1);
        assertThat(connected.webhook().uuid()).isEqualTo(sent.getSubscriptionUuid());
    }

    @Test
    void aLockedDeliveryIsSkippedByAConcurrentPoller() throws Exception {
        connect();
        UUID queued = fanOut.enqueue(new PaymentEvent(PaymentEvent.STK_COMPLETED, paybill.getUuid(),
                UUID.randomUUID(), clientId, Map.of("checkout_request_id", "ws_CO_1"))).get(0).getUuid();

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(s -> {
            deliveries.lockDue(Instant.now(), 50);
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        List<UUID> seen = new ArrayList<>();
        transactions.executeWithoutResult(s -> deliveries.lockDue(Instant.now(), 50)
                .forEach(row -> seen.add(row.getUuid())));
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);

        assertThat(seen).doesNotContain(queued);
        assertThat(transactions.execute(s -> deliveries.lockDue(Instant.now(), 50)).stream()
                .map(WebhookDelivery::getUuid)).contains(queued);
    }

    @Test
    void anStkResultReachesOnlyTheProductThatStartedThePush() {
        connect();
        String other = "shop-" + UUID.randomUUID().toString().substring(0, 8);
        products.connect(new ConnectProductRequest(other, "Shop",
                List.of(new ConnectProductRequest.GrantInput(paybill.getUuid(), Set.of(GrantOperation.STK_PUSH))),
                "https://api.shop.test/hooks/mpesa", List.of()));
        String checkout = "ws_CO_" + UUID.randomUUID().toString().substring(0, 8);
        MpesaIncomingPayment pending = new MpesaIncomingPayment();
        pending.setShortcodeUuid(paybill.getUuid());
        pending.setPaymentType(IncomingPaymentType.STK_PUSH);
        pending.setPhoneNumber("254708374149");
        pending.setAmount(BigDecimal.TEN);
        pending.setCheckoutRequestId(checkout);
        pending.setTransactionId(checkout);
        pending.setStatus(IncomingPaymentStatus.PENDING);
        pending.setClientId(clientId);
        UUID payment = incomingPayments.create(pending).getUuid();

        incomingPayments.processStkPushCallback(checkout, Map.of("ResultCode", 0, "ResultDesc", "Processed"));

        String sql = "SELECT COUNT(*) FROM webhook_deliveries WHERE client_id = ? AND event_type = 'stk.completed'";
        assertThat(jdbc.queryForObject(sql, Long.class, clientId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(sql, Long.class, other)).isZero();
        assertThat(timeline.eventsFor(payment)).singleElement().satisfies(step -> {
            assertThat(step.kind()).isEqualTo("webhook");
            assertThat(step.title()).isEqualTo("Webhook stk.completed to " + clientId + ": queued");
        });
    }

    private ConnectProductResponse connect() {
        return products.connect(new ConnectProductRequest(clientId, "Elimika",
                List.of(new ConnectProductRequest.GrantInput(paybill.getUuid(),
                        Set.of(GrantOperation.STK_PUSH, GrantOperation.C2B_FEED))),
                "https://api.elimika.test/hooks/mpesa", List.of()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
