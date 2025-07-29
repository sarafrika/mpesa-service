package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MpesaShortCodeRepository extends JpaRepository<MpesaShortCode, Long> {

    /**
     * Find shortcode configuration by UUID
     */
    Optional<MpesaShortCode> findByUuid(UUID uuid);

    /**
     * Find shortcode configuration by shortcode number
     */
    Optional<MpesaShortCode> findByShortcode(String shortcode);

    /**
     * Find all active shortcode configurations with pagination
     */
    Page<MpesaShortCode> findByIsActiveTrue(Pageable pageable);

    /**
     * Check if shortcode exists
     */
    boolean existsByShortcode(String shortcode);
}