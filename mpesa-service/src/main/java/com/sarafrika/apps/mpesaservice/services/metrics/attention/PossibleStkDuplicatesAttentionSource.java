package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.PendingStk;
import com.sarafrika.apps.mpesaservice.services.metrics.PaybillHealthService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** C2B deposits of the last week that more than one STK payment settled without a receipt may also be. */
@Component
@Order(35)
@RequiredArgsConstructor
public class PossibleStkDuplicatesAttentionSource implements AttentionSource {

    static final Duration LOOK_BACK = Duration.ofDays(7);

    private final MetricsQueries queries;
    private final PaybillHealthService health;

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        PendingStk flagged = queries.possibleStkDuplicates(health.now().minus(LOOK_BACK),
                new MetricsQueries.Filter(scope.shortcodeUuid(), scope.environment()));
        if (flagged.count() == 0) {
            return List.of();
        }
        String title = flagged.count() == 1 ? "1 C2B deposit may repeat an STK payment"
                : flagged.count() + " C2B deposits may repeat an STK payment";
        String link = "/transactions?type=C2B" + (scope.shortcodeUuid() == null ? "" : "&paybill=" + scope.shortcodeUuid());
        return List.of(new AttentionItemDto("POSSIBLE_STK_DUPLICATES", AttentionItemDto.WARNING, title,
                "Several STK payments of the same amount settled without a receipt around that time, so the deposit "
                        + "was kept and announced as C2B. Its raw callback lists them; check Safaricom's record for "
                        + "whether it is one of them.",
                link, scope.shortcodeUuid()));
    }
}
