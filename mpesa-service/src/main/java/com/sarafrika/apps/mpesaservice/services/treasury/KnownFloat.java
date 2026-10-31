package com.sarafrika.apps.mpesaservice.services.treasury;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A shortcode's newest known utility balance and where it came from; both null when none was reported. */
public record KnownFloat(BigDecimal utility, LocalDateTime reportedAt, String source) {

    public static final String BALANCE_QUERY = "BALANCE_QUERY";
    public static final String PAYOUT = "PAYOUT";
    public static final String C2B_PAYMENT = "C2B_PAYMENT";
    static final KnownFloat UNKNOWN = new KnownFloat(null, null, null);

    public boolean known() {
        return utility != null;
    }

    /** The newest of a balance result, a payout report and a C2B confirmation's OrgAccountBalance. A tie goes
     * to the balance result, then the payout; a report without a time loses to one with a time. */
    static KnownFloat newest(BigDecimal snapshotUtility, LocalDateTime snapshotAt, BigDecimal payoutUtility,
                             LocalDateTime payoutAt, BigDecimal paymentUtility, LocalDateTime paymentAt) {
        KnownFloat best = UNKNOWN;
        for (KnownFloat candidate : new KnownFloat[]{new KnownFloat(snapshotUtility, snapshotAt, BALANCE_QUERY),
                new KnownFloat(payoutUtility, payoutAt, PAYOUT), new KnownFloat(paymentUtility, paymentAt, C2B_PAYMENT)}) {
            if (candidate.known() && (!best.known() || newer(candidate.reportedAt(), best.reportedAt()))) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean newer(LocalDateTime candidate, LocalDateTime best) {
        return candidate != null && (best == null || candidate.isAfter(best));
    }
}
