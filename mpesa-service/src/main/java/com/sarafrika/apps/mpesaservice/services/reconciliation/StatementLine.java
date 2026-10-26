package com.sarafrika.apps.mpesaservice.services.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/** One line of Safaricom's record (an org statement row or a Pull Transactions item). Amounts are never negative:
 * paidIn is money into the account, withdrawn money out, whatever sign the export used. */
public record StatementLine(
        int lineNumber,
        String receipt,
        LocalDateTime completedAt,
        String details,
        String status,
        BigDecimal paidIn,
        BigDecimal withdrawn,
        String reasonType,
        String otherParty,
        String linkedTransactionId,
        String accountNumber,
        Map<String, String> raw) {

    public boolean completed() {
        return status == null || status.trim().equalsIgnoreCase("completed");
    }

    public boolean moneyIn() {
        return paidIn.compareTo(withdrawn) >= 0;
    }

    public BigDecimal amount() {
        return moneyIn() ? paidIn : withdrawn;
    }

    /** Reason Type and Details together, lower case, for the charge, reversal and transfer markers. */
    String description() {
        return ((reasonType == null ? "" : reasonType) + " " + (details == null ? "" : details)).toLowerCase(Locale.ROOT);
    }
}
