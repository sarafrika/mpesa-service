package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.CallbackInboxEntry;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface CallbackInboxRepository extends JpaRepository<CallbackInboxEntry, Long>,
        JpaSpecificationExecutor<CallbackInboxEntry> {

    Optional<CallbackInboxEntry> findByUuid(UUID uuid);

    long countByStatusAndReceivedAtGreaterThanEqual(CallbackStatus status, Instant since);

    @Query("SELECT e.status, COUNT(e) FROM CallbackInboxEntry e WHERE e.receivedAt >= :since GROUP BY e.status")
    List<Object[]> countByStatusSince(@Param("since") Instant since);

    /** Takes a failed (or stuck) entry for one replay; 0 rows means someone else has it or it needs none. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE CallbackInboxEntry e
            SET e.status = com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus.RECEIVED,
                e.attempts = e.attempts + 1, e.lastAttemptAt = :now
            WHERE e.uuid = :uuid
              AND (e.status = com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus.FAILED
                   OR (e.status = com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus.RECEIVED
                       AND COALESCE(e.lastAttemptAt, e.receivedAt) < :stuckBefore))
            """)
    int claimForReplay(@Param("uuid") UUID uuid, @Param("now") Instant now, @Param("stuckBefore") Instant stuckBefore);

    /** Deletes up to limit entries received before the cutoff, skipping rows another instance holds. */
    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM callback_inbox WHERE id IN (
                SELECT id FROM callback_inbox WHERE received_at < :before ORDER BY id LIMIT :limit
                FOR UPDATE SKIP LOCKED)
            """, nativeQuery = true)
    int deleteReceivedBefore(@Param("before") Instant before, @Param("limit") int limit);
}
