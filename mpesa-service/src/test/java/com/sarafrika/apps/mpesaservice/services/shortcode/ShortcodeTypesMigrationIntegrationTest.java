package com.sarafrika.apps.mpesaservice.services.shortcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V202611010101/02 on data written under the previous schema: paybills that paid out become One Accounts,
 * tills get store number = till number, and refused callbacks stop counting as failed. */
@Testcontainers
class ShortcodeTypesMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void existingShortcodesAndCallbacksAreCarriedOver() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        migrate(dataSource, "202610310102");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID paidB2c = shortcode(jdbc, "600101", "PAYBILL");
        UUID paidPochi = shortcode(jdbc, "600102", "PAYBILL");
        UUID collectsOnly = shortcode(jdbc, "600103", "PAYBILL");
        UUID till = shortcode(jdbc, "5123456", "TILL");
        UUID b2c = shortcode(jdbc, "600104", "B2C");
        jdbc.update("INSERT INTO mpesa_outgoing_payments (shortcode_uuid, payment_type, originator_conversation_id, "
                + "recipient_phone_number, amount, command_id, initiator_name, status) "
                + "VALUES (?, 'B2C', ?, '254712345678', 50, 'BusinessPayment', 'api', 'SUCCESS')",
                paidB2c, "sar-" + UUID.randomUUID());
        jdbc.update("INSERT INTO mpesa_outgoing_payments (shortcode_uuid, payment_type, originator_conversation_id, "
                + "recipient_phone_number, amount, command_id, initiator_name, status) "
                + "VALUES (?, 'B2B', ?, '600900', 50, 'BusinessPayBill', 'api', 'SUCCESS')",
                collectsOnly, "sar-" + UUID.randomUUID());
        jdbc.update("INSERT INTO product_payouts (shortcode_uuid, product, approval_request_uuid, command_id, party_b, "
                + "account_reference, amount, remarks) VALUES (?, 'BUSINESS_TO_POCHI', ?, 'BusinessPayToPochi', "
                + "'254712345678', 'ref', ?, 'Pochi')", paidPochi, UUID.randomUUID(), new BigDecimal("20"));
        jdbc.update("INSERT INTO callback_inbox (endpoint, kind, status, error) VALUES "
                + "('/api/v1/callbacks/x/stk/callback', 'STK', 'FAILED', 'Answered HTTP 401'), "
                + "('/api/v1/callbacks/stk/callback', 'STK', 'FAILED', 'No payment matches')");

        migrate(dataSource, null);

        assertThat(type(jdbc, paidB2c)).isEqualTo("ONE_ACCOUNT");
        assertThat(type(jdbc, paidPochi)).isEqualTo("ONE_ACCOUNT");
        assertThat(type(jdbc, collectsOnly)).isEqualTo("PAYBILL");
        assertThat(type(jdbc, b2c)).isEqualTo("B2C");
        assertThat(type(jdbc, till)).isEqualTo("TILL");
        assertThat(jdbc.queryForObject("SELECT store_number FROM mpesa_shortcodes WHERE uuid = ?", String.class, till))
                .isEqualTo("5123456");
        assertThat(jdbc.queryForObject("SELECT store_number_confirmed_at IS NULL FROM mpesa_shortcodes WHERE uuid = ?",
                Boolean.class, till)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mpesa_shortcodes WHERE store_number IS NOT NULL "
                + "AND shortcode_type <> 'TILL'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM callback_inbox ORDER BY id", String.class))
                .containsExactly("REJECTED", "FAILED");
        assertThatThrownBy(() -> shortcode(jdbc, "600105", "AGENT")).hasMessageContaining("shortcode_type_check");
        shortcode(jdbc, "600106", "ONE_ACCOUNT");
    }

    private static void migrate(DriverManagerDataSource dataSource, String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migrations");
        if (target != null) {
            config.target(target);
        }
        config.load().migrate();
    }

    private static UUID shortcode(JdbcTemplate jdbc, String number, String type) {
        return jdbc.queryForObject("INSERT INTO mpesa_shortcodes (shortcode, shortcode_type, business_name, "
                + "consumer_key, consumer_secret, callback_url, environment) VALUES (?, ?, 'Legacy', 'k', 's', "
                + "'https://example.test/cb', 'SANDBOX') RETURNING uuid", UUID.class, number, type);
    }

    private static String type(JdbcTemplate jdbc, UUID uuid) {
        return jdbc.queryForObject("SELECT shortcode_type FROM mpesa_shortcodes WHERE uuid = ?", String.class, uuid);
    }
}
