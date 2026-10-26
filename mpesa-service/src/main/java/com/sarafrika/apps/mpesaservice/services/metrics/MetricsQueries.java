package com.sarafrika.apps.mpesaservice.services.metrics;

import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aggregates straight over the payment tables in Postgres; soft-deleted rows never count. */
@Repository
@RequiredArgsConstructor
public class MetricsQueries {

    /** The two payment tables, with the columns that differ between them. A deposit counts when the money
     * arrived (its transaction time), so one backfilled from a statement lands on its own day, not the backfill's;
     * a backfill is always saved after that time, so created_at still bounds the scan (with a day of slack). */
    public enum Direction {
        IN("mpesa_incoming_payments", "COALESCE(p.transaction_id, p.checkout_request_id)", "p.account_reference",
                "COALESCE(p.transaction_date, p.created_at)"),
        OUT("mpesa_outgoing_payments", "COALESCE(p.transaction_id, p.conversation_id, p.originator_conversation_id)",
                "p.client_reference", "p.created_at");

        final String table;
        final String reference;
        final String accountReference;
        final String time;

        Direction(String table, String reference, String accountReference, String time) {
            this.table = table;
            this.reference = reference;
            this.accountReference = accountReference;
            this.time = time;
        }
    }

    private static final java.time.Duration SLACK = java.time.Duration.ofDays(1);

    public record Filter(UUID shortcodeUuid, Environment environment) {
        public static final Filter ALL = new Filter(null, null);
    }

    public record StatusTotal(boolean current, Direction direction, String paymentType, String status, long count,
                              BigDecimal amount) {
    }

    public record BucketTotal(Direction direction, LocalDateTime bucket, long count, BigDecimal amount) {
    }

    public record LatestFloat(UUID shortcodeUuid, String shortcode, String businessName, BigDecimal amount,
                              Instant reportedAt) {
    }

    public record ShortcodeRow(UUID uuid, String shortcode, String businessName, String shortcodeType,
                               String environment, boolean active, Instant createdAt, Instant lastActivityAt,
                               BigDecimal lowFloatThreshold) {
    }

    public record ShortcodeWindow(Direction direction, UUID shortcodeUuid, long todayCount, BigDecimal todayAmount,
                                  long failedRecent, long settledRecent) {
    }

    public record PendingStk(long count, Instant oldest) {
    }

    public record LatestRow(UUID uuid, Direction direction, String paymentType, String status, BigDecimal amount,
                            String reference, String accountReference, UUID shortcodeUuid, String shortcode,
                            String businessName, Instant createdAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** Totals by type and status for the current period [from, to) and an earlier one [previousFrom, previousTo). */
    public List<StatusTotal> statusTotals(Direction direction, Instant from, Instant to, Instant previousFrom,
                                          Instant previousTo, Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", MetricsTime.toStorage(from))
                .addValue("to", MetricsTime.toStorage(to))
                .addValue("previousFrom", MetricsTime.toStorage(previousFrom))
                .addValue("previousTo", MetricsTime.toStorage(previousTo))
                .addValue("scanFrom", MetricsTime.toStorage(earliest(from, previousFrom).minus(SLACK)));
        String t = direction.time;
        String sql = "SELECT " + t + " >= :from AS is_current, p.payment_type, p.status, COUNT(*) AS n, "
                + "COALESCE(SUM(p.amount), 0) AS total "
                + from(direction)
                + " AND p.created_at >= :scanFrom AND ((" + t + " >= :from AND " + t + " < :to)"
                + " OR (" + t + " >= :previousFrom AND " + t + " < :previousTo))" + scope(filter, params)
                + " GROUP BY 1, 2, 3";
        return jdbc.query(sql, params, (rs, i) -> new StatusTotal(rs.getBoolean("is_current"), direction,
                rs.getString("payment_type"), rs.getString("status"), rs.getLong("n"), rs.getBigDecimal("total")));
    }

    /** Successful payments per bucket; {@code unit} is hour or day, truncated in the reporting zone. */
    public List<BucketTotal> successBuckets(Direction direction, String unit, Instant from, Instant to,
                                            Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unit", unit)
                .addValue("storageZone", MetricsTime.storageZone().getId())
                .addValue("reportingZone", MetricsTime.REPORTING_ZONE.getId())
                .addValue("from", MetricsTime.toStorage(from))
                .addValue("to", MetricsTime.toStorage(to))
                .addValue("scanFrom", MetricsTime.toStorage(from.minus(SLACK)));
        String t = direction.time;
        String sql = "SELECT date_trunc(CAST(:unit AS text), (" + t + " AT TIME ZONE CAST(:storageZone AS text)) "
                + "AT TIME ZONE CAST(:reportingZone AS text)) AS bucket, COUNT(*) AS n, SUM(p.amount) AS total "
                + from(direction)
                + " AND p.status = 'SUCCESS' AND p.created_at >= :scanFrom AND " + t + " >= :from AND " + t + " < :to"
                + scope(filter, params) + " GROUP BY 1";
        return jdbc.query(sql, params, (rs, i) -> new BucketTotal(direction,
                rs.getTimestamp("bucket").toLocalDateTime(), rs.getLong("n"), rs.getBigDecimal("total")));
    }

    /** Each active shortcode's newest reported utility balance: an Account Balance result or a settled B2C
     * payout's report, whichever is newer (a tie goes to the balance result). */
    public List<LatestFloat> latestFloats(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String reports = "SELECT b.shortcode_uuid, b.utility_available AS amount, b.received_at AS reported_at, "
                + "0 AS preference, b.id FROM balance_snapshots b WHERE b.deleted_at IS NULL "
                + "AND b.status = 'COMPLETED' AND b.utility_available IS NOT NULL AND b.received_at IS NOT NULL "
                + "UNION ALL SELECT o.shortcode_uuid, o.utility_account_available_funds, "
                + "COALESCE(o.processed_at, o.updated_at), 1, o.id FROM mpesa_outgoing_payments o "
                + "WHERE o.deleted_at IS NULL AND o.utility_account_available_funds IS NOT NULL";
        String sql = "SELECT DISTINCT ON (p.shortcode_uuid) p.shortcode_uuid, s.shortcode, s.business_name, "
                + "p.amount, p.reported_at FROM (" + reports + ") p JOIN mpesa_shortcodes s ON s.uuid = p.shortcode_uuid "
                + "WHERE s.deleted_at IS NULL AND s.is_active" + scope(filter, params)
                + " ORDER BY p.shortcode_uuid, p.reported_at DESC NULLS LAST, p.preference, p.id DESC";
        return jdbc.query(sql, params, (rs, i) -> new LatestFloat(rs.getObject("shortcode_uuid", UUID.class),
                rs.getString("shortcode"), rs.getString("business_name"), rs.getBigDecimal("amount"),
                MetricsTime.fromStorage(rs.getTimestamp("reported_at"))));
    }

    /** Every shortcode that is not deleted, with its newest payment or callback in either table. */
    public List<ShortcodeRow> shortcodes(Environment environment) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("SELECT s.uuid, s.shortcode, s.business_name, s.shortcode_type, "
                + "s.environment, s.is_active, s.created_at, s.low_float_threshold, GREATEST(");
        List<String> latest = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            for (String column : List.of("created_at", "processed_at")) {
                latest.add("(SELECT MAX(p." + column + ") FROM " + direction.table
                        + " p WHERE p.shortcode_uuid = s.uuid AND p.deleted_at IS NULL)");
            }
        }
        sql.append(String.join(", ", latest)).append(") AS last_activity FROM mpesa_shortcodes s WHERE s.deleted_at IS NULL");
        if (environment != null) {
            sql.append(" AND s.environment = :environment");
            params.addValue("environment", environment.name());
        }
        sql.append(" ORDER BY s.is_active DESC, s.business_name, s.id");
        return jdbc.query(sql.toString(), params, (rs, i) -> new ShortcodeRow(rs.getObject("uuid", UUID.class),
                rs.getString("shortcode"), rs.getString("business_name"), rs.getString("shortcode_type"),
                rs.getString("environment"), rs.getBoolean("is_active"),
                MetricsTime.fromStorage(rs.getTimestamp("created_at")),
                MetricsTime.fromStorage(rs.getTimestamp("last_activity")), rs.getBigDecimal("low_float_threshold")));
    }

    /** Per shortcode: successful payments since {@code todayStart}, failed and settled ones since {@code recentStart}. */
    public List<ShortcodeWindow> shortcodeWindows(Direction direction, Instant todayStart, Instant recentStart) {
        Instant since = earliest(todayStart, recentStart).minus(SLACK);
        String t = direction.time;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("since", MetricsTime.toStorage(since))
                .addValue("todayStart", MetricsTime.toStorage(todayStart))
                .addValue("recentStart", MetricsTime.toStorage(recentStart));
        String sql = "SELECT p.shortcode_uuid, "
                + "COUNT(*) FILTER (WHERE p.status = 'SUCCESS' AND " + t + " >= :todayStart) AS today_count, "
                + "COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'SUCCESS' AND " + t + " >= :todayStart), 0) "
                + "AS today_amount, "
                + "COUNT(*) FILTER (WHERE p.status = 'FAILED' AND p.created_at >= :recentStart) AS failed_recent, "
                + "COUNT(*) FILTER (WHERE p.status <> 'PENDING' AND p.created_at >= :recentStart) AS settled_recent "
                + "FROM " + direction.table + " p WHERE p.deleted_at IS NULL AND p.created_at >= :since "
                + "GROUP BY p.shortcode_uuid";
        return jdbc.query(sql, params, (rs, i) -> new ShortcodeWindow(direction,
                rs.getObject("shortcode_uuid", UUID.class), rs.getLong("today_count"),
                rs.getBigDecimal("today_amount"), rs.getLong("failed_recent"), rs.getLong("settled_recent")));
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /** STK requests still pending that were created in [since, olderThan). */
    public PendingStk pendingStk(Instant since, Instant olderThan, Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("since", MetricsTime.toStorage(since))
                .addValue("olderThan", MetricsTime.toStorage(olderThan));
        String sql = "SELECT COUNT(*) AS n, MIN(p.created_at) AS oldest " + from(Direction.IN)
                + " AND p.payment_type = 'STK_PUSH' AND p.status = 'PENDING'"
                + " AND p.created_at >= :since AND p.created_at < :olderThan" + scope(filter, params);
        return jdbc.queryForObject(sql, params, (rs, i) -> new PendingStk(rs.getLong("n"),
                MetricsTime.fromStorage(rs.getTimestamp("oldest"))));
    }

    /** C2B confirmations since a time that several receiptless STK payments may also be. */
    public PendingStk possibleStkDuplicates(Instant since, Filter filter) {
        return flaggedTwins("C2B", since, filter);
    }

    /** Receiptless STK payments since a time that C2B deposits stored on their own may also be. */
    public PendingStk possibleC2bDuplicates(Instant since, Filter filter) {
        return flaggedTwins("STK_PUSH", since, filter);
    }

    private PendingStk flaggedTwins(String paymentType, Instant since, Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("since", MetricsTime.toStorage(since))
                .addValue("type", paymentType);
        String sql = "SELECT COUNT(*) AS n, MIN(p.created_at) AS oldest " + from(Direction.IN)
                + " AND p.payment_type = :type AND p.possible_stk_duplicates IS NOT NULL AND p.created_at >= :since"
                + scope(filter, params);
        return jdbc.queryForObject(sql, params, (rs, i) -> new PendingStk(rs.getLong("n"),
                MetricsTime.fromStorage(rs.getTimestamp("oldest"))));
    }

    /** C2B confirmations since a time that reached a shortcode not configured here. */
    public PendingStk unlinkedDeposits(Instant since) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("since", MetricsTime.toStorage(since));
        String sql = "SELECT COUNT(*) AS n, MIN(p.created_at) AS oldest FROM mpesa_incoming_payments p "
                + "WHERE p.deleted_at IS NULL AND p.shortcode_uuid IS NULL AND p.created_at >= :since";
        return jdbc.queryForObject(sql, params, (rs, i) -> new PendingStk(rs.getLong("n"),
                MetricsTime.fromStorage(rs.getTimestamp("oldest"))));
    }

    /** The newest payments across both tables, newest first. */
    public List<LatestRow> latest(int limit, Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        List<String> parts = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            parts.add("(SELECT p.uuid, '" + direction.name() + "' AS direction, p.payment_type, p.status, p.amount, "
                    + direction.reference + " AS reference, " + direction.accountReference + " AS account_reference, "
                    + "p.shortcode_uuid, s.shortcode, s.business_name, p.created_at, p.id "
                    + from(direction) + scope(filter, params)
                    + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit)");
        }
        String sql = String.join(" UNION ALL ", parts) + " ORDER BY created_at DESC, id DESC LIMIT :limit";
        return jdbc.query(sql, params, MetricsQueries::latestRow);
    }

    private static LatestRow latestRow(ResultSet rs, int i) throws SQLException {
        return new LatestRow(rs.getObject("uuid", UUID.class), Direction.valueOf(rs.getString("direction")),
                rs.getString("payment_type"), rs.getString("status"), rs.getBigDecimal("amount"),
                rs.getString("reference"), rs.getString("account_reference"),
                rs.getObject("shortcode_uuid", UUID.class), rs.getString("shortcode"), rs.getString("business_name"),
                MetricsTime.fromStorage(rs.getTimestamp("created_at")));
    }

    // LEFT JOIN: a C2B confirmation for a shortcode we do not know is still money that arrived.
    private static String from(Direction direction) {
        return "FROM " + direction.table + " p LEFT JOIN mpesa_shortcodes s ON s.uuid = p.shortcode_uuid "
                + "WHERE p.deleted_at IS NULL";
    }

    private static String scope(Filter filter, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder();
        if (filter.shortcodeUuid() != null) {
            sql.append(" AND p.shortcode_uuid = :shortcodeUuid");
            params.addValue("shortcodeUuid", filter.shortcodeUuid());
        }
        if (filter.environment() != null) {
            sql.append(" AND s.environment = :environment");
            params.addValue("environment", filter.environment().name());
        }
        return sql.toString();
    }
}
