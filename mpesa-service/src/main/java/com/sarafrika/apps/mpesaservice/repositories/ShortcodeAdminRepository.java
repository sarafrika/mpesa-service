package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Shortcodes as the console lists and searches them. */
@Repository
public interface ShortcodeAdminRepository extends JpaRepository<MpesaShortCode, Long>,
        JpaSpecificationExecutor<MpesaShortCode> {
}
