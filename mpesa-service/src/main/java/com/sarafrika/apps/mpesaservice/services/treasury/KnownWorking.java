package com.sarafrika.apps.mpesaservice.services.treasury;

import java.math.BigDecimal;
import java.time.Instant;

/** A shortcode's newest known Working (MMF) account balance, which B2B payments and B2C top-ups are paid from
 * (developer.safaricom.co.ke/apis/BusinessPayBill, /apis/B2CAccountTopUp); all null when none was reported. */
public record KnownWorking(BigDecimal available, Instant reportedAt, String source) {

    public static final String BALANCE_QUERY = KnownFloat.BALANCE_QUERY;
    public static final String B2B_RESULT = "B2B_RESULT";
    static final KnownWorking UNKNOWN = new KnownWorking(null, null, null);

    public boolean known() {
        return available != null;
    }

    /** The newer of a balance result and a B2B result; a tie goes to the balance result. */
    static KnownWorking newest(BigDecimal snapshot, Instant snapshotAt, BigDecimal transfer, Instant transferAt) {
        KnownWorking fromSnapshot = new KnownWorking(snapshot, snapshotAt, BALANCE_QUERY);
        KnownWorking fromTransfer = new KnownWorking(transfer, transferAt, B2B_RESULT);
        if (!fromTransfer.known()) {
            return fromSnapshot.known() ? fromSnapshot : UNKNOWN;
        }
        if (!fromSnapshot.known()) {
            return fromTransfer;
        }
        return transferAt != null && (snapshotAt == null || transferAt.isAfter(snapshotAt)) ? fromTransfer
                : fromSnapshot;
    }
}
