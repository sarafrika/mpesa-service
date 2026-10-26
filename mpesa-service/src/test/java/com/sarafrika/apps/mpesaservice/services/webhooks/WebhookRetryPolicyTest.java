package com.sarafrika.apps.mpesaservice.services.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WebhookRetryPolicyTest {

    @Test
    void retriesAfterTenSecondsOneMinuteTenMinutesAndAnHourThenGivesUp() {
        assertThat(WebhookRetryPolicy.delayAfter(1)).contains(Duration.ofSeconds(10));
        assertThat(WebhookRetryPolicy.delayAfter(2)).contains(Duration.ofMinutes(1));
        assertThat(WebhookRetryPolicy.delayAfter(3)).contains(Duration.ofMinutes(10));
        assertThat(WebhookRetryPolicy.delayAfter(4)).contains(Duration.ofHours(1));
        assertThat(WebhookRetryPolicy.delayAfter(5)).isEmpty();
        assertThat(WebhookRetryPolicy.delayAfter(0)).isEmpty();
        assertThat(WebhookRetryPolicy.MAX_ATTEMPTS).isEqualTo(5);
    }
}
