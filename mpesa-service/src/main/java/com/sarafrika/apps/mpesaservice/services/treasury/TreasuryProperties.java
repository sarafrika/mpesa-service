package com.sarafrika.apps.mpesaservice.services.treasury;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Our own settings, not Safaricom's: {@code balance-query-interval} asks every active shortcode with an
 * initiator for its balance that often (unset or 0 = off); {@code balance-query-poll} is how often the job
 * looks for due shortcodes, {@code balance-query-batch} how many it claims per pass. */
@ConfigurationProperties("mpesa.treasury")
public record TreasuryProperties(
        Duration balanceQueryInterval,
        @DefaultValue("1m") Duration balanceQueryPoll,
        @DefaultValue("20") int balanceQueryBatch) {

    public static TreasuryProperties defaults() {
        return new TreasuryProperties(null, Duration.ofMinutes(1), 20);
    }

    public boolean scheduledQueriesOn() {
        return balanceQueryInterval != null && !balanceQueryInterval.isZero() && !balanceQueryInterval.isNegative();
    }
}
