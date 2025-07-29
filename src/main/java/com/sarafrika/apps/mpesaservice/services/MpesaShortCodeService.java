package com.sarafrika.apps.mpesaservice.services;

import com.sarafrika.apps.mpesaservice.dtos.MpesaShortCodeDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MpesaShortCodeService {

    /**
     * Create a new M-Pesa shortcode configuration
     * @param dto the shortcode configuration data
     * @return the created shortcode configuration
     */
    MpesaShortCodeDto create(MpesaShortCodeDto dto);

    /**
     * Update an existing M-Pesa shortcode configuration
     * @param uuid the UUID of the shortcode to update
     * @param dto the updated shortcode configuration data
     * @return the updated shortcode configuration
     */
    MpesaShortCodeDto update(UUID uuid, MpesaShortCodeDto dto);

    /**
     * Find a shortcode configuration by UUID
     * @param uuid the UUID to search for
     * @return optional containing the shortcode if found
     */
    Optional<MpesaShortCodeDto> findByUuid(UUID uuid);

    /**
     * Find a shortcode configuration by shortcode number
     * @param shortcode the shortcode number to search for
     * @return optional containing the shortcode if found
     */
    Optional<MpesaShortCodeDto> findByShortcode(String shortcode);

    /**
     * Find all shortcode configurations with pagination
     * @param pageable pagination information
     * @return page of shortcode configurations
     */
    Page<MpesaShortCodeDto> findAll(Pageable pageable);

    /**
     * Find all active shortcode configurations with pagination
     * @param pageable pagination information
     * @return page of active shortcode configurations
     */
    Page<MpesaShortCodeDto> findAllActive(Pageable pageable);

    /**
     * Soft delete a shortcode configuration by UUID
     * @param uuid the UUID of the shortcode to delete
     */
    void deleteByUuid(UUID uuid);

    /**
     * Check if a shortcode exists
     * @param shortcode the shortcode number to check
     * @return true if exists, false otherwise
     */
    boolean existsByShortcode(String shortcode);
}