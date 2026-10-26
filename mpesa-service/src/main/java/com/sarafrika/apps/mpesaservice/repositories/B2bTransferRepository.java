package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.B2bTransfer;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import jakarta.persistence.LockModeType;
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

    @Query("""
            SELECT t FROM B2bTransfer t WHERE t.shortcodeUuid = :shortcode
              AND (:status IS NULL OR t.status = :status)""")
    Page<B2bTransfer> findForShortcode(@Param("shortcode") UUID shortcodeUuid,
                                       @Param("status") B2bTransferStatus status, Pageable pageable);
}
