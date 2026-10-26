package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.PendingStk;
import com.sarafrika.apps.mpesaservice.services.metrics.PaybillHealthService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** C2B money that reached a shortcode not configured here in the last week; it counts in no paybill. */
@Component
@Order(30)
@RequiredArgsConstructor
public class UnlinkedDepositsAttentionSource implements AttentionSource {

    static final Duration LOOK_BACK = Duration.ofDays(7);

    private final MetricsQueries queries;
    private final PaybillHealthService health;

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        // Unlinked deposits belong to no shortcode or environment, so a narrowed Overview never shows them.
        if (scope.shortcodeUuid() != null || scope.environment() != null) {
            return List.of();
        }
        PendingStk unlinked = queries.unlinkedDeposits(health.now().minus(LOOK_BACK));
        if (unlinked.count() == 0) {
            return List.of();
        }
        String title = unlinked.count() == 1 ? "1 unlinked deposit this week"
                : unlinked.count() + " unlinked deposits this week";
        return List.of(new AttentionItemDto("UNLINKED_DEPOSITS", AttentionItemDto.WARNING, title,
                "C2B payments reached a shortcode that is not configured here. Add the shortcode so they are "
                        + "counted and announced.",
                "/transactions?type=C2B", null));
    }
}
