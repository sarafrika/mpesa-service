package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.B2bTransfer;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface B2bTransferRepository extends JpaRepository<B2bTransfer, Long> {

    Optional<B2bTransfer> findByApprovalRequestUuid(UUID approvalRequestUuid);

    /** Row-locked reads, so two copies of one Safaricom result settle a transfer once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM B2bTransfer t WHERE t.uuid = :uuid")
    Optional<B2bTransfer> lockByUuid(@Param("uuid") UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM B2bTransfer t WHERE t.originatorConversationId = :id")
    Optional<B2bTransfer> lockByOriginatorConversationId(@Param("id") String originatorConversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM B2bTransfer t WHERE t.conversationId = :id")
    Optional<B2bTransfer> lockByConversationId(@Param("id") String conversationId);

    /** Transfers Safaricom never gave us ids for, that a result naming this receiver and amount may belong to. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM B2bTransfer t WHERE t.status = com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus.PENDING
              AND t.outcomeUnknown = true AND t.conversationId IS NULL AND t.originatorConversationId IS NULL
              AND t.receiverShortcode = :receiver AND t.amount = :amount AND t.createdAt >= :since""")
    List<B2bTransfer> lockUnmatched(@Param("receiver") String receiver, @Param("amount") BigDecimal amount,
                                    @Param("since") Instant since);

    /** Open transfers a stored result could be attached to by hand. */
    @Query("""
            SELECT t FROM B2bTransfer t WHERE t.status = com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus.PENDING
              AND t.conversationId IS NULL AND t.createdAt >= :since ORDER BY t.createdAt DESC, t.id DESC""")
    List<B2bTransfer> findAttachable(@Param("since") Instant since, Pageable pageable);

    Optional<B2bTransfer> findByUuidAndShortcodeUuid(UUID uuid, UUID shortcodeUuid);

    /** Successful transfers whose result reported the Working balance after, newest completion first. */
    @Query("""
            SELECT t FROM B2bTransfer t WHERE t.shortcodeUuid = :shortcode AND t.workingBalanceAfter IS NOT NULL
              AND t.status = com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus.SUCCESS
            ORDER BY COALESCE(t.transCompletedAt, t.completedAt) DESC, t.id DESC""")
    List<B2bTransfer> newestWithWorkingBalance(@Param("shortcode") UUID shortcodeUuid, Pageable pageable);

    @Query("""
            SELECT t FROM B2bTransfer t WHERE t.shortcodeUuid = :shortcode
              AND (:status IS NULL OR t.status = :status)""")
    Page<B2bTransfer> findForShortcode(@Param("shortcode") UUID shortcodeUuid,
                                       @Param("status") B2bTransferStatus status, Pageable pageable);
}
