package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.QrCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QrCodeRepository extends JpaRepository<QrCode, Long> {

    Page<QrCode> findByShortcodeUuid(UUID shortcodeUuid, Pageable pageable);

    Optional<QrCode> findByUuidAndShortcodeUuid(UUID uuid, UUID shortcodeUuid);
}
