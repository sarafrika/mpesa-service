package com.sarafrika.apps.mpesaservice.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V202611020101 on rows written before it: the service's own stamps were UTC wall time, Safaricom's were East
 * Africa Time, and the result must not depend on the session's TimeZone. */
@Testcontainers
class TimestampsAsInstantsMigrationIntegrationTest {

    private static final String BEFORE = "202611010103";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void utcStampsKeepTheirInstantAndSafaricomTimesAreReadAsNairobi() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        migrate(dataSource, BEFORE);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID shortcode = jdbc.queryForObject("INSERT INTO mpesa_shortcodes (shortcode, shortcode_type, business_name, "
                + "consumer_key, consumer_secret, callback_url, environment, last_connection_checked_at, created_at) "
                + "VALUES ('600111', 'PAYBILL', 'Fees', 'k', 's', 'https://example.test/cb', 'SANDBOX', ?, ?) "
                + "RETURNING uuid", UUID.class, wall("2026-06-02T14:20:00"), wall("2026-01-05T08:00:00"));
        // Payment UF2216NW3X: TransTime 20260602171907 (EAT), saved at 14:19:08 UTC.
        jdbc.update("INSERT INTO mpesa_incoming_payments (shortcode_uuid, payment_type, transaction_id, amount, status, "
                + "transaction_date, created_at, processed_at) VALUES (?, 'C2B', 'UF2216NW3X', 10, 'SUCCESS', ?, ?, ?)",
                shortcode, wall("2026-06-02T17:19:07"), wall("2026-06-02T14:19:08"), wall("2026-06-02T14:19:08"));
        // B2B Express Checkout rows: one stamped with Nairobi now(), one from before that fix with UTC now().
        jdbc.update("INSERT INTO mpesa_incoming_payments (shortcode_uuid, payment_type, transaction_id, amount, status, "
                + "transaction_date, created_at) VALUES (?, 'B2B_EXPRESS_CHECKOUT', 'B2BEAT', 10, 'SUCCESS', ?, ?), "
                + "(?, 'B2B_EXPRESS_CHECKOUT', 'B2BUTC', 10, 'SUCCESS', ?, ?)",
                shortcode, wall("2026-10-26T23:30:05"), wall("2026-10-26T20:30:00"),
                shortcode, wall("2026-10-26T20:30:05"), wall("2026-10-26T20:30:00"));
        jdbc.update("INSERT INTO mpesa_outgoing_payments (shortcode_uuid, payment_type, originator_conversation_id, "
                + "recipient_phone_number, amount, command_id, initiator_name, transaction_date, created_at) VALUES "
                + "(?, 'B2C', 'sar-1', '254700000000', 10, 'BusinessPayment', 'api', ?, ?)",
                shortcode, wall("2026-06-02T17:20:00"), wall("2026-06-02T14:19:58"));
        Long run = jdbc.queryForObject("INSERT INTO reconciliation_runs (shortcode_uuid, source, trigger_type, status, "
                + "period_start, period_end, created_at) VALUES (?, 'STATEMENT', 'MANUAL', 'COMPLETED', ?, ?, ?) "
                + "RETURNING id", Long.class, shortcode, wall("2026-06-02T00:00:00"), wall("2026-06-03T00:00:00"),
                wall("2026-06-03T06:00:00"));
        jdbc.update("INSERT INTO reconciliation_exceptions (run_id, shortcode_uuid, kind, direction, receipt, "
                + "suggested_fix, statement_time) VALUES (?, ?, 'MISSING_IN_LEDGER', 'IN', 'UF2216NW3Y', 'x', ?)",
                run, shortcode, wall("2026-06-02T17:19:07"));

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migrations")
                .initSql("SET TIME ZONE 'America/Los_Angeles'").load().migrate();

        assertThat(utc(jdbc, "mpesa_incoming_payments", "transaction_date", "transaction_id = 'UF2216NW3X'"))
                .isEqualTo("2026-06-02 14:19:07");
        assertThat(utc(jdbc, "mpesa_incoming_payments", "created_at", "transaction_id = 'UF2216NW3X'"))
                .isEqualTo("2026-06-02 14:19:08");
        assertThat(utc(jdbc, "mpesa_incoming_payments", "transaction_date", "transaction_id = 'B2BEAT'"))
                .isEqualTo("2026-10-26 20:30:05");
        assertThat(utc(jdbc, "mpesa_incoming_payments", "transaction_date", "transaction_id = 'B2BUTC'"))
                .isEqualTo("2026-10-26 20:30:05");
        assertThat(utc(jdbc, "mpesa_outgoing_payments", "transaction_date", "originator_conversation_id = 'sar-1'"))
                .isEqualTo("2026-06-02 14:20:00");
        assertThat(utc(jdbc, "mpesa_outgoing_payments", "created_at", "originator_conversation_id = 'sar-1'"))
                .isEqualTo("2026-06-02 14:19:58");
        assertThat(utc(jdbc, "mpesa_shortcodes", "last_connection_checked_at", "shortcode = '600111'"))
                .isEqualTo("2026-06-02 14:20:00");
        assertThat(utc(jdbc, "reconciliation_runs", "period_start", "id = " + run)).isEqualTo("2026-06-01 21:00:00");
        assertThat(utc(jdbc, "reconciliation_runs", "created_at", "id = " + run)).isEqualTo("2026-06-03 06:00:00");
        assertThat(utc(jdbc, "reconciliation_exceptions", "statement_time", "run_id = " + run))
                .isEqualTo("2026-06-02 14:19:07");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND data_type = 'timestamp without time zone' AND table_name <> 'flyway_schema_history'",
                Integer.class)).isZero();
        assertThat(utc(jdbc, "mpesa_transaction_feed", "transaction_date", "transaction_id = 'UF2216NW3X'"))
                .isEqualTo("2026-06-02 14:19:07");
        // Defaults still stamp new rows.
        jdbc.update("INSERT INTO mpesa_incoming_payments (payment_type, transaction_id, amount) VALUES ('C2B', 'NEW1', 1)");
        assertThat(jdbc.queryForObject("SELECT abs(extract(EPOCH FROM created_at - now())) < 60 "
                + "FROM mpesa_incoming_payments WHERE transaction_id = 'NEW1'", Boolean.class)).isTrue();
    }

    private static void migrate(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migrations").target(target).load().migrate();
    }

    /** Before the migration the columns are TIMESTAMP, so a wall time is stored as written. */
    private static LocalDateTime wall(String value) {
        return LocalDateTime.parse(value);
    }

    private static String utc(JdbcTemplate jdbc, String table, String column, String where) {
        return jdbc.queryForObject("SELECT to_char(" + column + " AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') FROM "
                + table + " WHERE " + where, String.class);
    }
}
