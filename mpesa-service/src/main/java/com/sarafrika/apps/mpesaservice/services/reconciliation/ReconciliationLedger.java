package com.sarafrika.apps.mpesaservice.services.reconciliation;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the ledger tables for reconciliation by receipt, case and spaces ignored. An STK request whose
 * transaction_id still holds its checkout id has no receipt yet: it is left out of matching and offered, by amount
 * and time, as the payment a statement receipt may belong to. */
@Repository
@RequiredArgsConstructor
public class ReconciliationLedger {

    private static final int CHUNK = 1000;

    private final NamedParameterJdbcTemplate jdbc;

    private static final String INCOMING = """
            SELECT id, uuid, 'IN' AS direction, payment_type AS type, transaction_id, amount, status,
                   account_reference, created_at, transaction_date
            FROM mpesa_incoming_payments
            WHERE deleted_at IS NULL AND transaction_id IS NOT NULL
              AND (checkout_request_id IS NULL OR transaction_id <> checkout_request_id)
              AND (shortcode_uuid = :shortcode OR (shortcode_uuid IS NULL AND business_short_code = :code))""";
    private static final String OUTGOING = """
            SELECT id, uuid, 'OUT' AS direction, payment_type AS type, transaction_id, amount, status,
                   client_reference AS account_reference, created_at, transaction_date
            FROM mpesa_outgoing_payments
            WHERE deleted_at IS NULL AND transaction_id IS NOT NULL AND shortcode_uuid = :shortcode""";
    private static final String B2B = """
            SELECT id, uuid, 'OUT' AS direction, 'B2B_TRANSFER' AS type, transaction_id, amount, status,
                   account_reference, created_at, completed_at AS transaction_date
            FROM b2b_transfers
            WHERE deleted_at IS NULL AND transaction_id IS NOT NULL AND shortcode_uuid = :shortcode""";
    private static final String PRODUCT = """
            SELECT id, uuid, 'OUT' AS direction, product AS type, transaction_id, amount, status,
                   account_reference, created_at, completed_at AS transaction_date
            FROM product_payouts
            WHERE deleted_at IS NULL AND transaction_id IS NOT NULL AND shortcode_uuid = :shortcode""";
    private static final String STK_CANDIDATES = """
            SELECT uuid, checkout_request_id, amount, phone_number, account_reference, client_id, created_at,
                   processed_at
            FROM mpesa_incoming_payments
            WHERE payment_type = 'STK_PUSH' AND status = 'SUCCESS' AND deleted_at IS NULL
              AND transaction_id = checkout_request_id AND shortcode_uuid = :shortcode AND amount = :amount
              AND created_at >= :from AND created_at <= :to
            ORDER BY created_at, id""";
    private static final String BY_RECEIPT = " AND upper(trim(transaction_id)) IN (:receipts)";
    private static final String IN_PERIOD = " AND status = 'SUCCESS'"
            + " AND COALESCE(transaction_date, created_at) >= :start AND COALESCE(transaction_date, created_at) < :end";

    /** Every live row holding one of these receipts (upper case), deposits only when incomingOnly. */
    public List<LedgerEntry> byReceipts(UUID shortcodeUuid, String shortcode, Collection<String> receipts,
                                        boolean incomingOnly) {
        List<LedgerEntry> found = new ArrayList<>();
        List<String> all = new ArrayList<>(receipts);
        for (int from = 0; from < all.size(); from += CHUNK) {
            MapSqlParameterSource params = params(shortcodeUuid, shortcode)
                    .addValue("receipts", all.subList(from, Math.min(all.size(), from + CHUNK)));
            found.addAll(jdbc.query(INCOMING + BY_RECEIPT, params, ReconciliationLedger::entry));
            if (!incomingOnly) {
                found.addAll(jdbc.query(OUTGOING + BY_RECEIPT, params, ReconciliationLedger::entry));
                found.addAll(jdbc.query(B2B + BY_RECEIPT, params, ReconciliationLedger::entry));
                found.addAll(jdbc.query(PRODUCT + BY_RECEIPT, params, ReconciliationLedger::entry));
            }
        }
        return found;
    }

    /** Settled rows whose transaction time (or, without one, creation time) falls in [start, end); C2B deposits
     * only when c2bOnly, for a source that returns nothing else. */
    public List<LedgerEntry> settledInPeriod(UUID shortcodeUuid, String shortcode, LocalDateTime start,
                                             LocalDateTime end, boolean c2bOnly) {
        MapSqlParameterSource params = params(shortcodeUuid, shortcode)
                .addValue("start", Timestamp.valueOf(start)).addValue("end", Timestamp.valueOf(end));
        List<LedgerEntry> found = new ArrayList<>(jdbc.query(INCOMING + (c2bOnly ? " AND payment_type = 'C2B'" : "")
                + IN_PERIOD, params, ReconciliationLedger::entry));
        if (!c2bOnly) {
            found.addAll(jdbc.query(OUTGOING + IN_PERIOD, params, ReconciliationLedger::entry));
            found.addAll(jdbc.query(B2B + IN_PERIOD.replace("transaction_date", "completed_at"), params,
                    ReconciliationLedger::entry));
            found.addAll(jdbc.query(PRODUCT + IN_PERIOD.replace("transaction_date", "completed_at"), params,
                    ReconciliationLedger::entry));
        }
        return found;
    }

    /** A settled STK payment still holding its checkout id: Safaricom's STK query reply has no receipt. */
    public record StkCandidate(UUID uuid, String checkoutRequestId, BigDecimal amount, String phoneNumber,
                               String accountReference, String clientId, LocalDateTime createdAt,
                               LocalDateTime processedAt) {
    }

    /** Receiptless STK payments of this amount requested within window of the deposit's time. */
    public List<StkCandidate> stkCandidates(UUID shortcodeUuid, BigDecimal amount, LocalDateTime at, Duration window) {
        if (shortcodeUuid == null || amount == null || at == null) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("shortcode", shortcodeUuid)
                .addValue("amount", amount).addValue("from", Timestamp.valueOf(at.minus(window)))
                .addValue("to", Timestamp.valueOf(at.plus(window)));
        return jdbc.query(STK_CANDIDATES, params, (rs, row) -> new StkCandidate(rs.getObject("uuid", UUID.class),
                rs.getString("checkout_request_id"), rs.getBigDecimal("amount"), rs.getString("phone_number"),
                rs.getString("account_reference"), rs.getString("client_id"), local(rs.getTimestamp("created_at")),
                local(rs.getTimestamp("processed_at"))));
    }

    /** Soft-deletes one duplicate deposit or payout row; nothing else about it changes. */
    public int softDelete(String table, UUID uuid, String actor) {
        if (!LedgerEntry.INCOMING.equals(table) && !LedgerEntry.OUTGOING.equals(table)) {
            throw new IllegalArgumentException("Only deposits and payouts can be merged");
        }
        return jdbc.update("UPDATE " + table + " SET deleted_at = CURRENT_TIMESTAMP, updated_by = :actor"
                + " WHERE uuid = :uuid AND deleted_at IS NULL", new MapSqlParameterSource()
                .addValue("uuid", uuid).addValue("actor", actor));
    }

    /** Marks a deposit added from Safaricom's record rather than its callback. */
    public void markSource(UUID paymentUuid, String source) {
        jdbc.update("UPDATE mpesa_incoming_payments SET source = :source WHERE uuid = :uuid",
                new MapSqlParameterSource().addValue("uuid", paymentUuid).addValue("source", source));
    }

    public String sourceOf(UUID paymentUuid) {
        return jdbc.query("SELECT source FROM mpesa_incoming_payments WHERE uuid = :uuid",
                new MapSqlParameterSource("uuid", paymentUuid), rs -> rs.next() ? rs.getString(1) : null);
    }

    private static MapSqlParameterSource params(UUID shortcodeUuid, String shortcode) {
        return new MapSqlParameterSource().addValue("shortcode", shortcodeUuid).addValue("code", shortcode);
    }

    private static LedgerEntry entry(ResultSet rs, int row) throws SQLException {
        String direction = rs.getString("direction");
        String type = rs.getString("type");
        String table = "B2B_TRANSFER".equals(type) ? LedgerEntry.B2B
                : "TAX_REMITTANCE".equals(type) || "BUSINESS_TO_POCHI".equals(type) ? LedgerEntry.PRODUCT
                : "IN".equals(direction) ? LedgerEntry.INCOMING : LedgerEntry.OUTGOING;
        return new LedgerEntry(rs.getLong("id"), rs.getObject("uuid", UUID.class), table, direction, type,
                rs.getString("transaction_id"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getString("account_reference"), local(rs.getTimestamp("created_at")),
                local(rs.getTimestamp("transaction_date")));
    }

    private static LocalDateTime local(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
