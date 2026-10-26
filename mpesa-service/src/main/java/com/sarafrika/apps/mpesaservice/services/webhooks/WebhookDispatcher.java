package com.sarafrika.apps.mpesaservice.services.webhooks;

import com.sarafrika.apps.mpesaservice.models.WebhookDelivery;
import com.sarafrika.apps.mpesaservice.models.WebhookSubscription;
import com.sarafrika.apps.mpesaservice.repositories.WebhookDeliveryRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookSubscriptionRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Sends due deliveries. Each poll claims rows with FOR UPDATE SKIP LOCKED and pushes next_attempt_at out by a
 * lease before sending, so several instances never send the same attempt and a crash only delays it. */
@Component
@Slf4j
public class WebhookDispatcher {

    static final String USER_AGENT = "Sarafrika-Webhooks/1";

    private final WebhookDeliveryRepository deliveries;
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookTransport transport;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private final int batchSize;
    private final Duration lease;
    private final ThreadPoolExecutor executor;
    private Clock clock = Clock.systemUTC();

    public WebhookDispatcher(WebhookDeliveryRepository deliveries, WebhookSubscriptionRepository subscriptions,
                             WebhookTransport transport, TransactionTemplate transactions,
                             @Value("${mpesa.webhooks.dispatcher.enabled:true}") boolean enabled,
                             @Value("${mpesa.webhooks.dispatcher.batch-size:20}") int batchSize,
                             @Value("${mpesa.webhooks.dispatcher.lease:2m}") Duration lease) {
        this.deliveries = deliveries;
        this.subscriptions = subscriptions;
        this.transport = transport;
        this.transactions = transactions;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.lease = lease;
        AtomicInteger threads = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), runnable -> {
            Thread thread = new Thread(runnable, "webhook-dispatch-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.DiscardPolicy());
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Sends whatever is due now on a background thread; the scheduled poll catches anything this misses. */
    public void kick() {
        if (!enabled) {
            return;
        }
        try {
            executor.execute(this::dispatchDueSafely);
        } catch (RejectedExecutionException e) {
            log.debug("Webhook dispatch already queued; the poller will pick this up");
        }
    }

    void dispatchDueSafely() {
        try {
            dispatchDue();
        } catch (RuntimeException e) {
            log.error("Webhook dispatch failed", e);
        }
    }

    /** Claims a batch of due deliveries and attempts each once; returns how many were attempted. */
    public int dispatchDue() {
        List<WebhookDelivery> claimed = claim();
        claimed.forEach(this::attempt);
        return claimed.size();
    }

    List<WebhookDelivery> claim() {
        Instant now = clock.instant();
        List<WebhookDelivery> due = transactions.execute(status -> {
            List<WebhookDelivery> rows = deliveries.lockDue(now, batchSize);
            rows.forEach(row -> row.setNextAttemptAt(now.plus(lease)));
            return deliveries.saveAll(rows);
        });
        return due == null ? List.of() : due;
    }

    /** One attempt at one delivery, recording the outcome and the next retry. */
    public void attempt(WebhookDelivery delivery) {
        Optional<WebhookSubscription> subscription = subscriptions.findByUuid(delivery.getSubscriptionUuid())
                .filter(WebhookSubscription::isActive);
        if (subscription.isEmpty()) {
            record(delivery.getId(), null, null, "The subscription was removed or disabled", true);
            return;
        }
        WebhookSubscription target = subscription.get();
        long sentAt = clock.instant().getEpochSecond();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", USER_AGENT);
        headers.put(WebhookSigner.EVENT_HEADER, delivery.getEventType());
        headers.put(WebhookSigner.DELIVERY_HEADER, delivery.getUuid().toString());
        headers.put(WebhookSigner.SIGNATURE_HEADER,
                WebhookSigner.signatureHeader(target.getSecret(), sentAt, delivery.getPayload()));

        long started = System.nanoTime();
        Integer code = null;
        String error = null;
        try {
            code = transport.post(URI.create(target.getUrl()), headers, delivery.getPayload());
            if (code < 200 || code >= 300) {
                error = "The receiver answered HTTP " + code;
            }
        } catch (HttpTimeoutException e) {
            error = "Timed out waiting for the receiver";
        } catch (IOException | IllegalArgumentException e) {
            error = "Could not reach the receiver: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "Interrupted while sending";
        }
        long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
        record(delivery.getId(), code, latency, error, false);
    }

    private void record(Long id, Integer code, Long latencyMs, String error, boolean giveUp) {
        transactions.executeWithoutResult(status -> deliveries.findById(id).ifPresent(row -> {
            Instant now = clock.instant();
            row.setAttempt(row.getAttempt() + 1);
            row.setResponseCode(code);
            row.setLatencyMs(latencyMs);
            if (error == null) {
                row.setStatus(WebhookDeliveryStatus.DELIVERED);
                row.setDeliveredAt(now);
                row.setNextAttemptAt(null);
                row.setError(null);
            } else {
                row.setError(error.length() > 1000 ? error.substring(0, 1000) : error);
                Optional<Duration> retry = giveUp ? Optional.empty() : WebhookRetryPolicy.delayAfter(row.getAttempt());
                row.setStatus(retry.isPresent() ? WebhookDeliveryStatus.PENDING : WebhookDeliveryStatus.FAILED);
                row.setNextAttemptAt(retry.map(now::plus).orElse(null));
            }
            deliveries.save(row);
        }));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
