package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** FAILED inbox entries per shortcode: the token URL they came in on, else the payment they were linked to.
 * Failures that name no shortcode either way (most unmatched results on the shared paths) are not counted. */
@Component
public class CallbackFailureCounts {

    public static final Duration WINDOW = Duration.ofHours(24);

    private final EntityManager entityManager;

    public CallbackFailureCounts(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> since(Instant since) {
        Map<UUID, Long> counts = new HashMap<>();
        entityManager.createQuery("""
                        SELECT COALESCE(c.shortcodeUuid, i.shortcodeUuid, o.shortcodeUuid), COUNT(c)
                        FROM CallbackInboxEntry c
                        LEFT JOIN MpesaIncomingPayment i ON i.uuid = c.paymentUuid
                        LEFT JOIN MpesaOutgoingPayment o ON o.uuid = c.paymentUuid
                        WHERE c.status = :failed AND c.receivedAt >= :since
                        GROUP BY COALESCE(c.shortcodeUuid, i.shortcodeUuid, o.shortcodeUuid)""", Object[].class)
                .setParameter("failed", CallbackStatus.FAILED).setParameter("since", since)
                .getResultList().forEach(row -> {
                    if (row[0] != null) {
                        counts.put((UUID) row[0], ((Number) row[1]).longValue());
                    }
                });
        return counts;
    }
}
