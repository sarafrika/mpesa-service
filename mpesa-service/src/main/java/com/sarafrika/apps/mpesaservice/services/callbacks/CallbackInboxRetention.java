package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.sarafrika.apps.mpesaservice.repositories.CallbackInboxRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes inbox entries received longer ago than mpesa.callbacks.inbox.retention, a batch at a time. Safe on
 * several instances: each batch skips rows another instance has locked, and deleting twice changes nothing. */
@Slf4j
@Component
public class CallbackInboxRetention {

    static final int BATCH = 500;
    static final int MAX_BATCHES = 20;

    private final CallbackInboxRepository repository;
    private final CallbackInboxProperties properties;
    private final Clock clock;

    public CallbackInboxRetention(CallbackInboxRepository repository, CallbackInboxProperties properties,
                                  ObjectProvider<Clock> clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock.getIfUnique(Clock::systemUTC);
    }

    @Scheduled(fixedDelayString = "${mpesa.callbacks.inbox.retention-interval:1h}", initialDelayString = "5m")
    public void run() {
        try {
            int deleted = purge();
            if (deleted > 0) {
                log.info("Deleted {} callback inbox entries older than {}", deleted, properties.retention());
            }
        } catch (RuntimeException ex) {
            log.error("Callback inbox retention failed; it runs again on the next tick", ex);
        }
    }

    /** One pass of at most MAX_BATCHES batches; returns how many entries it deleted. */
    public int purge() {
        if (properties.retention() == null || properties.retention().isZero() || properties.retention().isNegative()) {
            return 0;
        }
        Instant before = clock.instant().minus(properties.retention());
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int deleted = repository.deleteReceivedBefore(before, BATCH);
            total += deleted;
            if (deleted < BATCH) {
                break;
            }
        }
        return total;
    }
}
