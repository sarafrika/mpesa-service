package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PaybillHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PaybillState;
import com.sarafrika.apps.mpesaservice.services.metrics.PaybillHealthService;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Low float, quiet and failing production shortcodes, from the paybill health figures. */
@Component
@Order(10)
@RequiredArgsConstructor
public class PaybillAttentionSource implements AttentionSource {

    /** At least this many failures in the last hour, and more than half of what settled, means failing. */
    static final int FAILING_MIN = 3;
    /** Float under this share of the low-float threshold is critical rather than a warning. */
    static final BigDecimal CRITICAL_SHARE = new BigDecimal("0.2");

    private final PaybillHealthService health;

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        Instant now = health.now();
        List<AttentionItemDto> items = new ArrayList<>();
        for (PaybillHealthDto paybill : health.health(scope.environment())) {
            if (!scope.includes(paybill.shortcodeUuid()) || paybill.state() == PaybillState.SUSPENDED
                    || paybill.state() == PaybillState.DRAFT
                    || paybill.state() == PaybillState.SANDBOX) {
                continue;
            }
            String href = "/paybills/" + paybill.shortcodeUuid();
            String name = paybill.businessName() + " · " + paybill.shortcode();
            if (paybill.failedLastHour() >= FAILING_MIN && paybill.failedLastHour() * 2 > paybill.settledLastHour()) {
                items.add(new AttentionItemDto("PAYBILL_FAILING", AttentionItemDto.CRITICAL,
                        paybill.failedLastHour() + " payments failed on " + name + " in the last hour",
                        paybill.failedLastHour() + " of " + paybill.settledLastHour()
                                + " settled payments failed. Check the shortcode's credentials and callbacks.",
                        href, paybill.shortcodeUuid()));
            }
            if (paybill.state() == PaybillState.LOW_FLOAT) {
                BigDecimal lowFloat = paybill.lowFloatThreshold();
                boolean critical = paybill.floatAmount().compareTo(lowFloat.multiply(CRITICAL_SHARE)) < 0;
                items.add(new AttentionItemDto("LOW_FLOAT",
                        critical ? AttentionItemDto.CRITICAL : AttentionItemDto.WARNING,
                        paybill.businessName() + " float below " + kes(lowFloat),
                        "Utility account " + kes(paybill.floatAmount())
                                + " as last reported. Payouts fail once it runs out.",
                        href + "?tab=float", paybill.shortcodeUuid()));
            }
            if (paybill.state() == PaybillState.STALE) {
                Instant since = paybill.lastActivityAt();
                items.add(new AttentionItemDto("PAYBILL_STALE", AttentionItemDto.WARNING,
                        since == null ? "No traffic on " + name + " yet"
                                : "No traffic on " + name + " for " + span(Duration.between(since, now)),
                        "No payment or callback within " + span(health.alerts().staleAfter())
                                + ". Check the shortcode's URLs are registered with Safaricom.",
                        href, paybill.shortcodeUuid()));
            }
        }
        return items;
    }

    static String kes(BigDecimal amount) {
        return "KES " + new DecimalFormat("#,##0.00").format(amount);
    }

    static String span(Duration duration) {
        long hours = duration.toHours();
        if (hours >= 48) {
            return duration.toDays() + " days";
        }
        if (hours >= 1) {
            return hours + (hours == 1 ? " hour" : " hours");
        }
        long minutes = Math.max(1, duration.toMinutes());
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
