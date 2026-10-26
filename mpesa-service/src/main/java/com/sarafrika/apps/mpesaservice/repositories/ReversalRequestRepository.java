package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.ReversalRequest;
import com.sarafrika.apps.mpesaservice.utils.enums.ReversalStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReversalRequestRepository extends JpaRepository<ReversalRequest, Long> {

    Optional<ReversalRequest> findByApprovalUuid(UUID approvalUuid);

    List<ReversalRequest> findByPaymentUuidOrderByCreatedAtAscIdAsc(UUID paymentUuid);

    boolean existsByPaymentUuidAndStatusNotIn(UUID paymentUuid, Collection<ReversalStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReversalRequest r WHERE r.approvalUuid = :uuid")
    Optional<ReversalRequest> lockByApprovalUuid(@Param("uuid") UUID approvalUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReversalRequest r WHERE r.originatorConversationId = :id")
    Optional<ReversalRequest> lockByOriginatorConversationId(@Param("id") String originatorConversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReversalRequest r WHERE r.conversationId = :id")
    Optional<ReversalRequest> lockByConversationId(@Param("id") String conversationId);

    /** The open reversal of a receipt, for a result that cannot be matched by its conversation ids. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReversalRequest r WHERE r.transactionId = :receipt AND r.status IN :statuses")
    List<ReversalRequest> lockOpenByTransactionId(@Param("receipt") String receipt,
                                                  @Param("statuses") Collection<ReversalStatus> statuses);

    /** The payment table has no entity field for these two columns; only reversals write them. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE mpesa_incoming_payments SET reversed_at = :at, reversal_transaction_id = :receipt
            WHERE uuid = :payment AND reversed_at IS NULL
            """, nativeQuery = true)
    int markPaymentReversed(@Param("payment") UUID paymentUuid, @Param("at") Instant at,
                            @Param("receipt") String reversalReceipt);

    @Query(value = "SELECT COUNT(*) FROM mpesa_incoming_payments WHERE uuid = :payment AND reversed_at IS NOT NULL",
            nativeQuery = true)
    long countReversedPayment(@Param("payment") UUID paymentUuid);
}
