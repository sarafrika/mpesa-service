package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.WebhookDelivery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long>,
        JpaSpecificationExecutor<WebhookDelivery> {

    Optional<WebhookDelivery> findByUuid(UUID uuid);

    /** Due rows, locked so another instance polling at the same time skips them. Postgres only. */
    @Query(value = """
            SELECT * FROM webhook_deliveries
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDelivery> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    /** Per subscription since a time: total, delivered on the first attempt, failed, retrying, newest. */
    @Query("""
            SELECT d.subscriptionUuid, COUNT(d),
                   SUM(CASE WHEN d.status = com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus.DELIVERED
                            AND d.attempt = 1 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN d.status = com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus.FAILED
                            THEN 1 ELSE 0 END),
                   SUM(CASE WHEN d.status = com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus.PENDING
                            AND d.attempt > 0 THEN 1 ELSE 0 END),
                   MAX(d.createdAt)
            FROM WebhookDelivery d
            WHERE d.createdAt >= :since
            GROUP BY d.subscriptionUuid
            """)
    List<Object[]> healthSince(@Param("since") Instant since);
}
