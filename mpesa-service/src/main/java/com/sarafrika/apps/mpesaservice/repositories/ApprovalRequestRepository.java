package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.ApprovalRequest;
import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long>,
        JpaSpecificationExecutor<ApprovalRequest> {

    Optional<ApprovalRequest> findByUuid(UUID uuid);

    /** Row lock, so two checkers (or the runner and the expiry job) never decide one request twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ApprovalRequest a WHERE a.uuid = :uuid")
    Optional<ApprovalRequest> lockByUuid(@Param("uuid") UUID uuid);

    long countByStatus(ApprovalStatus status);

    long countByStatusAndMakerSub(ApprovalStatus status, String makerSub);

    @Query("SELECT a.uuid FROM ApprovalRequest a WHERE a.status = :status AND a.expiresAt < :now ORDER BY a.expiresAt")
    List<UUID> findDue(@Param("status") ApprovalStatus status, @Param("now") Instant now, Pageable pageable);

    /** Approved but never claimed, or executing with a heartbeat older than the lease. */
    @Query("""
            SELECT a.uuid FROM ApprovalRequest a
            WHERE (a.status = 'APPROVED' AND a.decidedAt < :approvedBefore)
               OR (a.status = 'EXECUTING' AND (a.heartbeatAt IS NULL OR a.heartbeatAt < :staleBefore))
            ORDER BY a.decidedAt
            """)
    List<UUID> findResumable(@Param("approvedBefore") Instant approvedBefore,
                             @Param("staleBefore") Instant staleBefore, Pageable pageable);

    @Query("SELECT a FROM ApprovalRequest a WHERE a.status = 'WAITING' ORDER BY a.createdAt")
    List<ApprovalRequest> findWaiting();

    @Modifying
    @Query("UPDATE ApprovalRequest a SET a.heartbeatAt = :now WHERE a.uuid = :uuid AND a.status = 'EXECUTING'")
    int touch(@Param("uuid") UUID uuid, @Param("now") Instant now);
}
