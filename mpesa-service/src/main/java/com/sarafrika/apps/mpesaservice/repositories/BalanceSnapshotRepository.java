package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.BalanceSnapshot;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceSnapshotStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, Long> {

    /** Row-locked, so two copies of one result settle a query once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BalanceSnapshot b WHERE b.originatorConversationId = :id")
    List<BalanceSnapshot> lockByOriginatorConversationId(@Param("id") String originatorConversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BalanceSnapshot b WHERE b.conversationId = :id")
    List<BalanceSnapshot> lockByConversationId(@Param("id") String conversationId);

    Optional<BalanceSnapshot> findFirstByShortcodeUuidAndStatusOrderByReceivedAtDescIdDesc(
            UUID shortcodeUuid, BalanceSnapshotStatus status);

    Optional<BalanceSnapshot> findFirstByShortcodeUuidOrderByRequestedAtDescIdDesc(UUID shortcodeUuid);

    /** Completed snapshots received in [from, to), oldest first, for the history chart. */
    @Query("""
            SELECT b FROM BalanceSnapshot b
            WHERE b.shortcodeUuid = :shortcode AND b.status = :status
              AND b.receivedAt >= :from AND b.receivedAt < :to
            ORDER BY b.receivedAt ASC, b.id ASC""")
    List<BalanceSnapshot> history(@Param("shortcode") UUID shortcodeUuid,
                                  @Param("status") BalanceSnapshotStatus status,
                                  @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                  Pageable limit);

    /** The newest completed snapshot of each of these shortcodes. */
    @Query("""
            SELECT b FROM BalanceSnapshot b WHERE b.id IN (
                SELECT MAX(b2.id) FROM BalanceSnapshot b2
                WHERE b2.shortcodeUuid IN :ids AND b2.status = :status AND b2.utilityAvailable IS NOT NULL
                GROUP BY b2.shortcodeUuid)""")
    List<BalanceSnapshot> latestCompleted(@Param("ids") List<UUID> shortcodeUuids,
                                          @Param("status") BalanceSnapshotStatus status);
}
