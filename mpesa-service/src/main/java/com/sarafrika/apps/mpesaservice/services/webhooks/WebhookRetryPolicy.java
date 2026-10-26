package com.sarafrika.apps.mpesaservice.services.webhooks;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** The first attempt goes straight away; failures retry after 10 s, 1 min, 10 min and 1 h, then give up. */
public final class WebhookRetryPolicy {

    public static final List<Duration> DELAYS = List.of(
            Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(10), Duration.ofHours(1));
    public static final int MAX_ATTEMPTS = DELAYS.size() + 1;

    private WebhookRetryPolicy() {
    }

    /** The wait before the next attempt after {@code attemptsMade} failed ones; empty when none are left. */
    public static Optional<Duration> delayAfter(int attemptsMade) {
        if (attemptsMade < 1 || attemptsMade > DELAYS.size()) {
            return Optional.empty();
        }
        return Optional.of(DELAYS.get(attemptsMade - 1));
    }
}
