package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.PayoutBatch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutBatchRepository extends JpaRepository<PayoutBatch, Long> {

    Optional<PayoutBatch> findByUuid(UUID uuid);

    Optional<PayoutBatch> findByApprovalUuid(UUID approvalUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PayoutBatch b WHERE b.approvalUuid = :uuid")
    Optional<PayoutBatch> lockByApprovalUuid(@Param("uuid") UUID approvalUuid);
}
