package com.sarafrika.apps.mpesaservice.services.callbacks;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code mpesa.callbacks.inbox}: bodies above max-body-bytes are not stored; an entry still RECEIVED after
 * stuck-after may be replayed; more failures than failure-alert-threshold in the last hour is critical on the
 * Overview; entries older than retention are deleted (0 keeps them). Plain settings, not Safaricom's policy. */
@ConfigurationProperties("mpesa.callbacks.inbox")
public record CallbackInboxProperties(
        @DefaultValue("65536") int maxBodyBytes,
        @DefaultValue("5m") Duration stuckAfter,
        @DefaultValue("0") int failureAlertThreshold,
        @DefaultValue("90d") Duration retention) {

    public static CallbackInboxProperties defaults() {
        return new CallbackInboxProperties(65536, Duration.ofMinutes(5), 0, Duration.ofDays(90));
    }
}
