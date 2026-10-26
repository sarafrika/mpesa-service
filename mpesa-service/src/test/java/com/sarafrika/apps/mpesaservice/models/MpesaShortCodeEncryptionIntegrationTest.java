package com.sarafrika.apps.mpesaservice.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shortcode secrets are ciphertext in the table, plaintext on the entity, and legacy rows still read. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SecretCipher.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MpesaShortCodeEncryptionIntegrationTest {

    private static final String[] SECRETS = {
            "consumer_key", "consumer_secret", "passkey", "initiator_password", "security_credential"};

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MpesaShortCodeRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void secretsAreEncryptedAtRestAndDecryptedOnLoad() {
        MpesaShortCode code = shortcode("600100");
        code.setConsumerKey("k".repeat(255));
        code.setConsumerSecret("s".repeat(500));
        code.setPasskey("bfb279f9aa9bdbcf158e97dd71a467cd");
        code.setInitiatorPassword("Safaricom999!*!");
        code.setSecurityCredential("CRED==");
        MpesaShortCode saved = repository.saveAndFlush(code);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM mpesa_shortcodes WHERE id = ?", saved.getId());
        for (String column : SECRETS) {
            assertThat((String) row.get(column)).as(column).startsWith(SecretCipher.PREFIX);
        }
        MpesaShortCode loaded = repository.findByUuid(saved.getUuid()).orElseThrow();
        assertThat(loaded.getConsumerKey()).isEqualTo("k".repeat(255));
        assertThat(loaded.getConsumerSecret()).isEqualTo("s".repeat(500));
        assertThat(loaded.getPasskey()).isEqualTo("bfb279f9aa9bdbcf158e97dd71a467cd");
        assertThat(loaded.getInitiatorPassword()).isEqualTo("Safaricom999!*!");
        assertThat(loaded.getSecurityCredential()).isEqualTo("CRED==");
    }

    @Test
    void legacyPlaintextRowsReadAsIsAndAreEncryptedOnTheNextSave() {
        jdbc.update("""
                INSERT INTO mpesa_shortcodes (shortcode, shortcode_type, business_name, consumer_key, consumer_secret,
                    passkey, callback_url, environment) VALUES ('600200', 'PAYBILL', 'Legacy', 'old-key', 'old-secret',
                    'old-passkey', 'https://example.test/cb', 'SANDBOX')""");

        MpesaShortCode legacy = repository.findByShortcode("600200").orElseThrow();
        assertThat(legacy.getConsumerSecret()).isEqualTo("old-secret");

        legacy.setBusinessName("Legacy, re-saved");
        repository.saveAndFlush(legacy);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM mpesa_shortcodes WHERE shortcode = '600200'");
        assertThat((String) row.get("consumer_secret")).startsWith(SecretCipher.PREFIX);
        assertThat((String) row.get("passkey")).startsWith(SecretCipher.PREFIX);
        assertThat(repository.findByShortcode("600200").orElseThrow().getPasskey()).isEqualTo("old-passkey");
    }

    private static MpesaShortCode shortcode(String number) {
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(number);
        code.setShortcodeType(ShortcodeType.PAYBILL);
        code.setBusinessName("Encrypted");
        code.setCallbackUrl("https://example.test/api/v1/callbacks/stk/callback");
        code.setEnvironment(Environment.SANDBOX);
        return code;
    }
}
