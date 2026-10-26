package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.ReconciliationRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    Optional<ReconciliationRun> findByUuid(UUID uuid);

    Page<ReconciliationRun> findByShortcodeUuid(UUID shortcodeUuid, Pageable pageable);
}
