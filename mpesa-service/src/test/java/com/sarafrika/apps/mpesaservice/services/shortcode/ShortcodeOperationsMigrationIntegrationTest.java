package com.sarafrika.apps.mpesaservice.services.shortcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V202611010103 on shortcodes saved before operations: each keeps everything its type allows, and a stored
 * initiator credential is dated from the row's last update. */
@Testcontainers
class ShortcodeOperationsMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void existingShortcodesKeepEverythingTheirTypeAllows() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        migrate(dataSource, "202611010102");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID paybill = shortcode(jdbc, "600101", "PAYBILL", null);
        UUID till = shortcode(jdbc, "5123456", "TILL", null);
        UUID b2c = shortcode(jdbc, "600102", "B2C", "pw");
        UUID both = shortcode(jdbc, "600103", "ONE_ACCOUNT", null);
        LocalDateTime updated = LocalDateTime.of(2026, 9, 1, 8, 30);
        jdbc.update("UPDATE mpesa_shortcodes SET updated_at = ? WHERE uuid = ?", updated, b2c);

        migrate(dataSource, null);

        assertThat(operations(jdbc, paybill)).isEqualTo("C2B,STK_PUSH,REVERSAL,TRANSACTION_STATUS,ACCOUNT_BALANCE");
        assertThat(operations(jdbc, till)).isEqualTo("C2B,STK_PUSH,REVERSAL,TRANSACTION_STATUS,ACCOUNT_BALANCE");
        assertThat(operations(jdbc, b2c)).isEqualTo("TRANSACTION_STATUS,ACCOUNT_BALANCE,B2C_PAYOUT,POCHI");
        assertThat(operations(jdbc, both))
                .isEqualTo("C2B,STK_PUSH,REVERSAL,TRANSACTION_STATUS,ACCOUNT_BALANCE,B2C_PAYOUT,POCHI");
        assertThat(jdbc.queryForObject("SELECT initiator_credential_set_at FROM mpesa_shortcodes WHERE uuid = ?",
                LocalDateTime.class, b2c)).isEqualTo(updated);
        assertThat(jdbc.queryForObject("SELECT initiator_credential_set_at FROM mpesa_shortcodes WHERE uuid = ?",
                LocalDateTime.class, paybill)).isNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE mpesa_shortcodes SET operations = 'C2B,QR' WHERE uuid = ?",
                paybill)).hasMessageContaining("chk_mpesa_shortcodes_operations");
        jdbc.update("UPDATE mpesa_shortcodes SET operations = '' WHERE uuid = ?", paybill);
    }

    private static void migrate(DriverManagerDataSource dataSource, String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migrations");
        if (target != null) {
            config.target(target);
        }
        config.load().migrate();
    }

    private static UUID shortcode(JdbcTemplate jdbc, String number, String type, String initiatorPassword) {
        return jdbc.queryForObject("INSERT INTO mpesa_shortcodes (shortcode, shortcode_type, business_name, "
                + "consumer_key, consumer_secret, callback_url, environment, initiator_password, store_number) "
                + "VALUES (?, ?, 'Legacy', 'k', 's', 'https://example.test/cb', 'SANDBOX', ?, ?) RETURNING uuid",
                UUID.class, number, type, initiatorPassword, "TILL".equals(type) ? number : null);
    }

    private static String operations(JdbcTemplate jdbc, UUID uuid) {
        return jdbc.queryForObject("SELECT operations FROM mpesa_shortcodes WHERE uuid = ?", String.class, uuid);
    }
}
