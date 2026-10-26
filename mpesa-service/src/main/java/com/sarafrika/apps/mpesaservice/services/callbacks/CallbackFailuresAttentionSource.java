package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.repositories.CallbackInboxRepository;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionScope;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionSource;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Callbacks whose handling failed in the last hour and were not replayed since; critical above the
 * configured mpesa.callbacks.inbox.failure-alert-threshold. Callbacks belong to no paybill filter. */
@Component
@Order(40)
public class CallbackFailuresAttentionSource implements AttentionSource {

    static final String KIND = "CALLBACKS_FAILING";
    static final Duration WINDOW = Duration.ofHours(1);

    private final CallbackInboxRepository repository;
    private final CallbackInboxProperties properties;
    private final Clock clock;

    public CallbackFailuresAttentionSource(CallbackInboxRepository repository, CallbackInboxProperties properties,
                                           ObjectProvider<Clock> clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock.getIfUnique(Clock::systemUTC);
    }

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        if (scope.shortcodeUuid() != null || scope.environment() != null) {
            return List.of();
        }
        long failed = repository.countByStatusAndReceivedAtGreaterThanEqual(CallbackStatus.FAILED,
                clock.instant().minus(WINDOW));
        if (failed <= properties.failureAlertThreshold()) {
            return List.of();
        }
        String title = failed == 1 ? "1 callback failed in the last hour" : failed + " callbacks failed in the last hour";
        return List.of(new AttentionItemDto(KIND, AttentionItemDto.CRITICAL, title,
                "Safaricom was answered but the callback was not applied. Check the error and replay it.",
                "/callbacks?status=FAILED", null));
    }
}
