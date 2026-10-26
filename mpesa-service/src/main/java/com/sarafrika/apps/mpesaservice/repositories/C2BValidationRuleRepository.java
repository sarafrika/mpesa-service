package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.C2BValidationRule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface C2BValidationRuleRepository extends JpaRepository<C2BValidationRule, Long> {

    Optional<C2BValidationRule> findByShortcodeUuid(UUID shortcodeUuid);
}
