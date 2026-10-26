package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.WebhookSubscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {

    Optional<WebhookSubscription> findByUuid(UUID uuid);

    List<WebhookSubscription> findByClientId(String clientId);

    List<WebhookSubscription> findByClientIdInAndActiveTrue(Collection<String> clientIds);

    List<WebhookSubscription> findByUuidIn(Collection<UUID> uuids);

    Page<WebhookSubscription> findByClientId(String clientId, Pageable pageable);
}
