package com.sarafrika.apps.mpesaservice.services.approvals;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** Settings of this service (not Safaricom or Sarafrika policy): how long a request waits for a checker,
 * how long a silent execution is trusted before it is resumed, the console's payout client id, and the
 * CSV limits of a bulk payout upload. */
@ConfigurationProperties("mpesa.approvals")
public record ApprovalProperties(
        @DefaultValue("48h") Duration expireAfter,
        @DefaultValue("15m") Duration executionLease,
        @DefaultValue("mpesa-console") String consoleClientId,
        @DefaultValue("512KB") DataSize bulkMaxFileSize,
        @DefaultValue("1000") int bulkMaxRows) {

    public static ApprovalProperties defaults() {
        return new ApprovalProperties(Duration.ofHours(48), Duration.ofMinutes(15), "mpesa-console",
                DataSize.ofKilobytes(512), 1000);
    }
}
