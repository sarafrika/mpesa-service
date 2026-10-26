package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientShortcodeGrantRepository extends JpaRepository<ClientShortcodeGrant, Long>,
        JpaSpecificationExecutor<ClientShortcodeGrant> {

    Optional<ClientShortcodeGrant> findByClientIdAndShortcodeUuid(String clientId, UUID shortcodeUuid);

    List<ClientShortcodeGrant> findByClientId(String clientId);

    List<ClientShortcodeGrant> findByShortcodeUuid(UUID shortcodeUuid);

    List<ClientShortcodeGrant> findByClientIdAndActiveTrue(String clientId);

    List<ClientShortcodeGrant> findByShortcodeUuidAndActiveTrue(UUID shortcodeUuid);
}
