package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.PendingStk;
import com.sarafrika.apps.mpesaservice.services.metrics.PaybillHealthService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** C2B deposits of the last week that more than one STK payment settled without a receipt may also be, and
 * STK payments settled without a receipt that C2B deposits stored on their own may also be. */
@Component
@Order(35)
@RequiredArgsConstructor
public class PossibleStkDuplicatesAttentionSource implements AttentionSource {

    static final Duration LOOK_BACK = Duration.ofDays(7);

    private final MetricsQueries queries;
    private final PaybillHealthService health;

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        MetricsQueries.Filter filter = new MetricsQueries.Filter(scope.shortcodeUuid(), scope.environment());
        List<AttentionItemDto> items = new ArrayList<>();
        PendingStk flagged = queries.possibleStkDuplicates(health.now().minus(LOOK_BACK), filter);
        if (flagged.count() > 0) {
            items.add(c2bItem(flagged, scope));
        }
        PendingStk stk = queries.possibleC2bDuplicates(health.now().minus(LOOK_BACK), filter);
        if (stk.count() > 0) {
            String title = stk.count() == 1 ? "1 STK payment may repeat a C2B deposit"
                    : stk.count() + " STK payments may repeat a C2B deposit";
            String link = "/transactions?type=STK_PUSH"
                    + (scope.shortcodeUuid() == null ? "" : "&paybill=" + scope.shortcodeUuid());
            items.add(new AttentionItemDto("POSSIBLE_C2B_DUPLICATES", AttentionItemDto.WARNING, title,
                    "These STK payments settled from Safaricom's status query without a receipt, and the C2B "
                            + "deposits of the same amount stored around then fit more than one payment, so none was "
                            + "merged. Each raw callback lists them; check Safaricom's record for which one it is.",
                    link, scope.shortcodeUuid()));
        }
        return items;
    }

    private static AttentionItemDto c2bItem(PendingStk flagged, AttentionScope scope) {
        String title = flagged.count() == 1 ? "1 C2B deposit may repeat an STK payment"
                : flagged.count() + " C2B deposits may repeat an STK payment";
        String link = "/transactions?type=C2B" + (scope.shortcodeUuid() == null ? "" : "&paybill=" + scope.shortcodeUuid());
        return new AttentionItemDto("POSSIBLE_STK_DUPLICATES", AttentionItemDto.WARNING, title,
                "Several STK payments of the same amount settled without a receipt around that time, so the deposit "
                        + "was kept and announced as C2B. Its raw callback lists them; check Safaricom's record for "
                        + "whether it is one of them.",
                link, scope.shortcodeUuid());
    }
}
