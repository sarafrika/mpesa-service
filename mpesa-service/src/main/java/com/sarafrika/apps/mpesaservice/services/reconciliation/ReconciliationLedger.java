package com.sarafrika.apps.mpesaservice.services.reconciliation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
 * transaction_id still holds its checkout id has no receipt yet and is left out. */
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
            }
        }
        return found;
    }

    /** Settled rows whose transaction time (or, without one, creation time) falls in [start, end). */
    public List<LedgerEntry> settledInPeriod(UUID shortcodeUuid, String shortcode, LocalDateTime start,
                                             LocalDateTime end, boolean incomingOnly) {
        MapSqlParameterSource params = params(shortcodeUuid, shortcode)
                .addValue("start", Timestamp.valueOf(start)).addValue("end", Timestamp.valueOf(end));
        List<LedgerEntry> found = new ArrayList<>(jdbc.query(INCOMING + IN_PERIOD, params, ReconciliationLedger::entry));
        if (!incomingOnly) {
            found.addAll(jdbc.query(OUTGOING + IN_PERIOD, params, ReconciliationLedger::entry));
            found.addAll(jdbc.query(B2B + IN_PERIOD.replace("transaction_date", "completed_at"), params,
                    ReconciliationLedger::entry));
        }
        return found;
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
