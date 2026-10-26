package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.C2bRegistration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface C2bRegistrationRepository extends JpaRepository<C2bRegistration, Long> {

    Page<C2bRegistration> findByShortcodeUuid(UUID shortcodeUuid, Pageable pageable);

    Optional<C2bRegistration> findFirstByShortcodeUuidOrderByCreatedAtDescIdDesc(UUID shortcodeUuid);

    /** The newest attempt of each of these shortcodes. */
    @Query("""
            SELECT r FROM C2bRegistration r WHERE r.id IN (
                SELECT MAX(r2.id) FROM C2bRegistration r2 WHERE r2.shortcodeUuid IN :uuids GROUP BY r2.shortcodeUuid)
            """)
    List<C2bRegistration> findLatestFor(@Param("uuids") Collection<UUID> uuids);
}
