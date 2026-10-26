package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.ProductPayout;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.ProductPayoutStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductPayoutRepository extends JpaRepository<ProductPayout, Long> {

    Optional<ProductPayout> findByApprovalRequestUuid(UUID approvalRequestUuid);

    /** Row-locked reads, so two copies of one Safaricom result settle a payout once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductPayout p WHERE p.uuid = :uuid")
    Optional<ProductPayout> lockByUuid(@Param("uuid") UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductPayout p WHERE p.product = :product AND p.originatorConversationId = :id")
    Optional<ProductPayout> lockByOriginatorConversationId(@Param("product") DarajaFeature product,
                                                           @Param("id") String originatorConversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductPayout p WHERE p.product = :product AND p.conversationId = :id")
    Optional<ProductPayout> lockByConversationId(@Param("product") DarajaFeature product,
                                                 @Param("id") String conversationId);

    @Query("""
            SELECT p FROM ProductPayout p WHERE p.shortcodeUuid = :shortcode AND p.product = :product
              AND (:status IS NULL OR p.status = :status)""")
    Page<ProductPayout> findForShortcode(@Param("shortcode") UUID shortcodeUuid,
                                         @Param("product") DarajaFeature product,
                                         @Param("status") ProductPayoutStatus status, Pageable pageable);
}
