package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.B2bCheckoutRequest;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bCheckoutStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface B2bCheckoutRequestRepository extends JpaRepository<B2bCheckoutRequest, Long> {

    /** Row-locked, so two copies of one callback settle a checkout once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM B2bCheckoutRequest c WHERE c.requestRefId = :id")
    Optional<B2bCheckoutRequest> lockByRequestRefId(@Param("id") UUID requestRefId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM B2bCheckoutRequest c WHERE c.uuid = :uuid")
    Optional<B2bCheckoutRequest> lockByUuid(@Param("uuid") UUID uuid);

    @Query("""
            SELECT c FROM B2bCheckoutRequest c WHERE c.shortcodeUuid = :shortcode
              AND (:status IS NULL OR c.status = :status)""")
    Page<B2bCheckoutRequest> findForShortcode(@Param("shortcode") UUID shortcodeUuid,
                                              @Param("status") B2bCheckoutStatus status, Pageable pageable);
}
