package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.PullTransactionRegistration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PullTransactionRegistrationRepository extends JpaRepository<PullTransactionRegistration, Long> {

    Optional<PullTransactionRegistration> findByShortcodeUuid(UUID shortcodeUuid);
}
