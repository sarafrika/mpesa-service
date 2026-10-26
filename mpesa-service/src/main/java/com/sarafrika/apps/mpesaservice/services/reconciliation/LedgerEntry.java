package com.sarafrika.apps.mpesaservice.services.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/** One live row of our own ledger: a deposit (IN), a payout (OUT), a B2B transfer (OUT, b2b_transfers) or a Tax
 * Remittance or Pochi payment (OUT, product_payouts). */
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
    public static final String PRODUCT = "product_payouts";

    public String key() {
        return receipt.trim().toUpperCase(Locale.ROOT);
    }

    /** Only deposits and B2C payouts can be asked about through Transaction Status here. */
    public static boolean queryable(String type) {
        return type != null && !"B2B_TRANSFER".equals(type) && !"TAX_REMITTANCE".equals(type)
                && !"BUSINESS_TO_POCHI".equals(type);
    }

    public boolean settled() {
        return "SUCCESS".equals(status);
    }
}
