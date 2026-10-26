package com.sarafrika.apps.mpesaservice.services.metrics;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code mpesa.alerts.low-float}: float (KES) below which a production shortcode shows LOW_FLOAT;
 * {@code mpesa.alerts.stale-after}: how long it may go without traffic before it is STALE. */
@ConfigurationProperties("mpesa.alerts")
public record AlertProperties(
        @DefaultValue("50000") BigDecimal lowFloat,
        @DefaultValue("6h") Duration staleAfter) {

    public static AlertProperties defaults() {
        return new AlertProperties(BigDecimal.valueOf(50_000), Duration.ofHours(6));
    }
}
