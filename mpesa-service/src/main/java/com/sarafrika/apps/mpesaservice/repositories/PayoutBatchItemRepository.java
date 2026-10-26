package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.PayoutBatchItem;
import com.sarafrika.apps.mpesaservice.utils.enums.PayoutBatchItemStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutBatchItemRepository extends JpaRepository<PayoutBatchItem, Long> {

    List<PayoutBatchItem> findByBatchIdAndStatusInOrderByLineNumberAsc(Long batchId,
                                                                       Collection<PayoutBatchItemStatus> statuses);

    long countByBatchIdAndStatusNot(Long batchId, PayoutBatchItemStatus status);

    /** Each item with its payout (or null), in file order; a batch holds at most the upload's row limit. */
    @Query("""
            SELECT i, p FROM PayoutBatchItem i LEFT JOIN MpesaOutgoingPayment p ON p.uuid = i.payoutUuid
            WHERE i.batchId = :batchId ORDER BY i.lineNumber, i.id
            """)
    List<Object[]> findWithPayouts(@Param("batchId") Long batchId);

    /** Payouts that already used one of the references: the console's own at any time (repeating its
     * reference would return the old payout instead of paying), anyone's since the given time. */
    @Query("""
            SELECT p.clientReference, p.createdAt FROM MpesaOutgoingPayment p
            WHERE p.clientReference IN :references AND (p.clientId = :consoleClient OR p.createdAt >= :since)
            """)
    List<Object[]> findUsedReferences(@Param("references") Collection<String> references,
                                      @Param("consoleClient") String consoleClient,
                                      @Param("since") java.time.LocalDateTime since);

    /** Claims a reference for a batch; the primary key refuses a reference another open batch holds. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO payout_reference_claims (reference, batch_id) VALUES (:reference, :batchId)",
            nativeQuery = true)
    int claimReference(@Param("reference") String reference, @Param("batchId") Long batchId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM payout_reference_claims WHERE batch_id = :batchId", nativeQuery = true)
    int releaseReferences(@Param("batchId") Long batchId);

    /** References already in a batch that is waiting or running, so one row cannot be queued twice. */
    @Query("""
            SELECT i.reference FROM PayoutBatchItem i, PayoutBatch b
            WHERE b.id = i.batchId AND i.reference IN :references
              AND b.status IN (com.sarafrika.apps.mpesaservice.utils.enums.PayoutBatchStatus.WAITING,
                               com.sarafrika.apps.mpesaservice.utils.enums.PayoutBatchStatus.RUNNING)
            """)
    List<String> findOpenReferences(@Param("references") Collection<String> references);
}
