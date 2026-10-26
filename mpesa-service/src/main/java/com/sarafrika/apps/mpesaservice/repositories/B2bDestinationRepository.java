package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.B2bDestination;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface B2bDestinationRepository extends JpaRepository<B2bDestination, Long> {

    Optional<B2bDestination> findByUuidAndShortcodeUuid(UUID uuid, UUID shortcodeUuid);

    Optional<B2bDestination> findByUuid(UUID uuid);

    List<B2bDestination> findByUuidIn(Collection<UUID> uuids);

    @Query("""
            SELECT d FROM B2bDestination d WHERE d.shortcodeUuid = :shortcode
              AND (:active IS NULL OR d.active = :active)""")
    Page<B2bDestination> findForShortcode(@Param("shortcode") UUID shortcodeUuid, @Param("active") Boolean active,
                                          Pageable pageable);

    @Query("""
            SELECT COUNT(d) > 0 FROM B2bDestination d WHERE d.shortcodeUuid = :shortcode
              AND LOWER(d.name) = LOWER(:name) AND (:except IS NULL OR d.uuid <> :except)""")
    boolean nameTaken(@Param("shortcode") UUID shortcodeUuid, @Param("name") String name,
                      @Param("except") UUID exceptUuid);
}
