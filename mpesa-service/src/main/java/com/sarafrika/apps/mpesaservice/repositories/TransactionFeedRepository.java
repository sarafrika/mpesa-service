package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.TransactionFeedEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** Read-only access to the mpesa_transaction_feed view. */
public interface TransactionFeedRepository extends Repository<TransactionFeedEntry, UUID>,
        JpaSpecificationExecutor<TransactionFeedEntry> {

    Optional<TransactionFeedEntry> findById(UUID id);
}
