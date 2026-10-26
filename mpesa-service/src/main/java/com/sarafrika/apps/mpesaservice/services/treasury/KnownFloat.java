package com.sarafrika.apps.mpesaservice.services.treasury;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A shortcode's newest known utility balance and where it came from; both null when none was reported. */
public record KnownFloat(BigDecimal utility, LocalDateTime reportedAt, String source) {

    public static final String BALANCE_QUERY = "BALANCE_QUERY";
    public static final String PAYOUT = "PAYOUT";
    static final KnownFloat UNKNOWN = new KnownFloat(null, null, null);

    public boolean known() {
        return utility != null;
    }

    /** The newer of a balance result and a payout report; a tie goes to the balance result. */
    static KnownFloat newest(BigDecimal snapshotUtility, LocalDateTime snapshotAt, BigDecimal payoutUtility,
                             LocalDateTime payoutAt) {
        boolean snapshot = snapshotUtility != null;
        boolean payout = payoutUtility != null;
        if (snapshot && (!payout || payoutAt == null || (snapshotAt != null && !payoutAt.isAfter(snapshotAt)))) {
            return new KnownFloat(snapshotUtility, snapshotAt, BALANCE_QUERY);
        }
        return payout ? new KnownFloat(payoutUtility, payoutAt, PAYOUT) : UNKNOWN;
    }
}
