package com.sarafrika.apps.mpesaservice.services.webhooks;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Polls for due deliveries (retries, and anything a kick missed); off with mpesa.webhooks.dispatcher.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "mpesa.webhooks.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class WebhookPollerConfig {

    private final WebhookDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${mpesa.webhooks.dispatcher.poll-interval:5s}", initialDelayString = "10s")
    public void poll() {
        dispatcher.dispatchDueSafely();
    }
}
