package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.TransactionStatusQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionStatusQueryRepository extends JpaRepository<TransactionStatusQuery, Long> {

    List<TransactionStatusQuery> findTop20ByPaymentUuidOrderByCreatedAtDesc(UUID paymentUuid);

    Optional<TransactionStatusQuery> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);

    Optional<TransactionStatusQuery> findFirstByOriginatorConversationIdOrderByCreatedAtDesc(String originatorConversationId);
}
