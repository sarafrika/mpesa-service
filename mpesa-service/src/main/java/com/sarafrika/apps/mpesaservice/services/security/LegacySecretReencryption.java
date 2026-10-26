package com.sarafrika.apps.mpesaservice.services.security;

import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-off at startup when mpesa.security.reencrypt-legacy-secrets is true: seals secret columns still
 * holding plaintext from before encryption. Idempotent (sealed values are skipped, each update checks the old
 * value), audited with counts only, and it never logs a value.
 */
@Slf4j
@Component
public class LegacySecretReencryption implements ApplicationRunner {

    static final Map<String, List<String>> COLUMNS = new LinkedHashMap<>(Map.of(
            "mpesa_shortcodes", List.of("consumer_key", "consumer_secret", "passkey", "initiator_password",
                    "security_credential"),
            "webhook_subscriptions", List.of("secret")));

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final ObjectProvider<AuditService> audit;
    private final TransactionTemplate transaction;
    private final boolean enabled;

    public LegacySecretReencryption(JdbcTemplate jdbc, SecretCipher cipher, ObjectProvider<AuditService> audit,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${mpesa.security.reencrypt-legacy-secrets:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            reencrypt();
        }
    }

    /** Returns how many values were sealed; 0 once every secret is encrypted. */
    public int reencrypt() {
        int rows = 0;
        int values = 0;
        for (Map.Entry<String, List<String>> table : COLUMNS.entrySet()) {
            List<String> columns = table.getValue();
            String plaintext = columns.stream()
                    .map(c -> "(" + c + " IS NOT NULL AND " + c + " NOT LIKE '" + SecretCipher.PREFIX + "%')")
                    .collect(Collectors.joining(" OR "));
            List<Map<String, Object>> found = jdbc.queryForList("SELECT id, " + String.join(", ", columns)
                    + " FROM " + table.getKey() + " WHERE " + plaintext);
            for (Map<String, Object> row : found) {
                Integer sealed = transaction.execute(status -> seal(table.getKey(), columns, row));
                if (sealed != null && sealed > 0) {
                    rows++;
                    values += sealed;
                }
            }
        }
        log.info("Legacy secret re-encryption sealed {} values in {} rows", values, rows);
        if (values > 0) {
            Map<String, Object> after = Map.of("rows", rows, "values", values);
            AuditService trail = audit.getIfAvailable();
            if (trail != null) {
                transaction.executeWithoutResult(status ->
                        trail.record("LEGACY_SECRETS_REENCRYPTED", "SECRETS", null, null, after));
            }
        }
        return values;
    }

    private int seal(String table, List<String> columns, Map<String, Object> row) {
        int sealed = 0;
        for (String column : columns) {
            Object value = row.get(column);
            if (value instanceof String text && !SecretCipher.isEncrypted(text)) {
                // The old value in the WHERE clause leaves a value changed meanwhile alone.
                sealed += jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ? AND " + column + " = ?",
                        cipher.encrypt(text), row.get("id"), text);
            }
        }
        return sealed;
    }
}
