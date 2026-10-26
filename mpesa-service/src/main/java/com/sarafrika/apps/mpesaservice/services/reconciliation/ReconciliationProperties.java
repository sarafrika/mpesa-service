package com.sarafrika.apps.mpesaservice.services.reconciliation;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Settings of this service, not Safaricom limits: upload size and rows, the longest period, the words that mark
 * a statement line as a charge, reversal or internal transfer (unverified against real exports), and the nightly run. */
@ConfigurationProperties(prefix = "mpesa.reconciliation")
public record ReconciliationProperties(
        DataSize maxFileSize,
        Integer maxRows,
        Integer maxPeriodDays,
        List<String> chargeMarkers,
        List<String> reversalMarkers,
        List<String> transferMarkers,
        Nightly nightly,
        Pull pull) {

    public ReconciliationProperties {
        maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(5) : maxFileSize;
        maxRows = maxRows == null ? 50_000 : maxRows;
        maxPeriodDays = maxPeriodDays == null ? 31 : maxPeriodDays;
        chargeMarkers = chargeMarkers == null ? List.of("charge") : chargeMarkers;
        reversalMarkers = reversalMarkers == null ? List.of("reversal") : reversalMarkers;
        transferMarkers = transferMarkers == null ? List.of("settlement account", "funds movement") : transferMarkers;
        nightly = nightly == null ? new Nightly(false, null) : nightly;
        pull = pull == null ? new Pull(null, null) : pull;
    }

    /** Reconciles yesterday through Pull Transactions for every shortcode with the feature on; off by default. */
    public record Nightly(boolean enabled, String cron) {
        public Nightly {
            cron = cron == null || cron.isBlank() ? "0 30 2 * * *" : cron;
        }
    }

    /** callbackUrl is sent as Register's CallBackURL; maxPages bounds one query's OffSetValue paging. */
    public record Pull(String callbackUrl, Integer maxPages) {
        public Pull {
            maxPages = maxPages == null ? 50 : maxPages;
        }
    }
}
