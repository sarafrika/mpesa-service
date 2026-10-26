package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.ReconciliationExceptionRecord;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconciliationExceptionRepository extends JpaRepository<ReconciliationExceptionRecord, Long>,
        JpaSpecificationExecutor<ReconciliationExceptionRecord> {

    Optional<ReconciliationExceptionRecord> findByUuid(UUID uuid);

    /** Resolve actions lock the exception so two people never act on it at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ReconciliationExceptionRecord e WHERE e.uuid = :uuid")
    Optional<ReconciliationExceptionRecord> lockByUuid(@Param("uuid") UUID uuid);

    List<ReconciliationExceptionRecord> findByRunId(Long runId);

    /** Kind, status, count and statement value per kind for one run. */
    @Query("""
            SELECT e.kind, e.status, COUNT(e), COALESCE(SUM(COALESCE(e.statementAmount, e.ledgerAmount)), 0)
            FROM ReconciliationExceptionRecord e WHERE e.runId = :runId GROUP BY e.kind, e.status""")
    List<Object[]> summarise(@Param("runId") Long runId);

    @Query("""
            SELECT e.runId, COUNT(e) FROM ReconciliationExceptionRecord e
            WHERE e.runId IN :runIds
              AND e.status = com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionStatus.OPEN
            GROUP BY e.runId""")
    List<Object[]> countOpenByRunIds(@Param("runIds") List<Long> runIds);

    long countByRunIdAndStatus(Long runId, ReconciliationExceptionStatus status);

    /** Open exceptions per shortcode, for the Overview's attention list. */
    @Query("""
            SELECT e.shortcodeUuid, COUNT(e) FROM ReconciliationExceptionRecord e
            WHERE e.status = com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionStatus.OPEN
            GROUP BY e.shortcodeUuid""")
    List<Object[]> countOpenByShortcode();

    /** Earlier runs whose whole period a new run covered (of this source only, when given): their open
     * exceptions were checked again. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE reconciliation_exceptions SET status = 'SUPERSEDED', updated_at = CURRENT_TIMESTAMP
            WHERE status = 'OPEN' AND shortcode_uuid = :shortcode AND run_id <> :runId
              AND run_id IN (SELECT id FROM reconciliation_runs
                             WHERE shortcode_uuid = :shortcode AND period_start >= :start AND period_end <= :end
                               AND (CAST(:source AS VARCHAR) IS NULL OR source = CAST(:source AS VARCHAR)))""",
            nativeQuery = true)
    int supersede(@Param("shortcode") UUID shortcodeUuid, @Param("runId") Long runId,
                  @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                  @Param("source") String source);
}
