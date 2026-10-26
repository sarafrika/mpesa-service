package com.sarafrika.apps.mpesaservice.services.approvals;

import java.util.Locale;

/** The request types this service knows; each has one ApprovalExecutor bean. */
public final class ApprovalTypes {

    public static final String REVERSAL = "REVERSAL";
    public static final String BULK_PAYOUT = "BULK_PAYOUT";
    public static final String B2B_SWEEP = "B2B_SWEEP";

    private ApprovalTypes() {
    }

    /** "BULK_PAYOUT" reads "Bulk payout". */
    public static String label(String type) {
        String words = type.replace('_', ' ').toLowerCase(Locale.ROOT);
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
