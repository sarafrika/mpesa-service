package com.sarafrika.apps.mpesaservice.services.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/** One live row of our own ledger: a deposit (IN), a payout (OUT) or a B2B transfer (OUT, table b2b_transfers). */
public record LedgerEntry(
        long id,
        UUID uuid,
        String table,
        String direction,
        String type,
        String receipt,
        BigDecimal amount,
        String status,
        String accountReference,
        LocalDateTime createdAt,
        LocalDateTime transactionDate) {

    public static final String INCOMING = "mpesa_incoming_payments";
    public static final String OUTGOING = "mpesa_outgoing_payments";
    public static final String B2B = "b2b_transfers";

    public String key() {
        return receipt.trim().toUpperCase(Locale.ROOT);
    }

    public boolean settled() {
        return "SUCCESS".equals(status);
    }
}
