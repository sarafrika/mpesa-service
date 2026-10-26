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

/** STK pushes still waiting for Safaricom's callback after five minutes (looking back one day). */
@Component
@Order(20)
@RequiredArgsConstructor
public class PendingStkAttentionSource implements AttentionSource {

    static final Duration PENDING_AFTER = Duration.ofMinutes(5);
    static final Duration LOOK_BACK = Duration.ofDays(1);

    private final MetricsQueries queries;
    private final PaybillHealthService health;

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        Instant now = health.now();
        PendingStk pending = queries.pendingStk(now.minus(LOOK_BACK), now.minus(PENDING_AFTER),
                new MetricsQueries.Filter(scope.shortcodeUuid(), scope.environment()));
        if (pending.count() == 0) {
            return List.of();
        }
        String title = pending.count() == 1 ? "1 STK request pending over 5 minutes"
                : pending.count() + " STK requests pending over 5 minutes";
        String oldest = pending.oldest() == null ? ""
                : " The oldest has waited " + PaybillAttentionSource.span(Duration.between(pending.oldest(), now)) + ".";
        return List.of(new AttentionItemDto("STK_PENDING", AttentionItemDto.INFO, title,
                "The customer has not answered the prompt, or Safaricom's callback has not arrived." + oldest,
                "/transactions?status=PENDING&payment_type=STK_PUSH", scope.shortcodeUuid()));
    }
}
