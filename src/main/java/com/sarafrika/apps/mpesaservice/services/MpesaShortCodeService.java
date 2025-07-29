package com.sarafrika.apps.mpesaservice.services;

import com.sarafrika.apps.mpesaservice.dtos.MpesaShortCodeDto;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MpesaShortCodeService {

    /**
     * Create a new M-Pesa shortcode configuration
     *
     * @param shortCodeDto The shortcode configuration data
     * @return Created shortcode with generated UUID and timestamps
     * @throws IllegalArgumentException if shortcode already exists or validation fails
     */
    MpesaShortCodeDto create(MpesaShortCodeDto shortCodeDto);

    /**
     * Update an existing M-Pesa shortcode configuration
     *
     * @param uuid The UUID of the shortcode to update
     * @param shortCodeDto The updated shortcode configuration data
     * @return Updated shortcode configuration
     * @throws IllegalArgumentException if shortcode not found or validation fails
     */
    MpesaShortCodeDto update(UUID uuid, MpesaShortCodeDto shortCodeDto);

    /**
     * Partially update an existing M-Pesa shortcode configuration
     * Only non-null fields will be updated
     *
     * @param uuid The UUID of the shortcode to update
     * @param shortCodeDto The partial shortcode configuration data
     * @return Updated shortcode configuration
     * @throws IllegalArgumentException if shortcode not found
     */
    MpesaShortCodeDto partialUpdate(UUID uuid, MpesaShortCodeDto shortCodeDto);

    /**
     * Find shortcode by UUID
     *
     * @param uuid The UUID to search for
     * @return Optional containing the shortcode if found
     */
    Optional<MpesaShortCodeDto> findByUuid(UUID uuid);

    /**
     * Find shortcode by shortcode number
     *
     * @param shortcode The shortcode number to search for
     * @return Optional containing the shortcode if found
     */
    Optional<MpesaShortCodeDto> findByShortcode(String shortcode);

    /**
     * Find all shortcodes with pagination
     *
     * @param pageable Pagination parameters
     * @return Page of shortcode configurations
     */
    Page<MpesaShortCodeDto> findAll(Pageable pageable);

    /**
     * Find all active shortcodes with pagination
     *
     * @param pageable Pagination parameters
     * @return Page of active shortcode configurations
     */
    Page<MpesaShortCodeDto> findAllActive(Pageable pageable);

    /**
     * Find shortcodes by type with pagination
     *
     * @param shortcodeType The shortcode type to filter by
     * @param pageable Pagination parameters
     * @return Page of shortcode configurations matching the type
     */
    Page<MpesaShortCodeDto> findByShortcodeType(ShortcodeType shortcodeType, Pageable pageable);

    /**
     * Find shortcodes by environment with pagination
     *
     * @param environment The environment to filter by
     * @param pageable Pagination parameters
     * @return Page of shortcode configurations matching the environment
     */
    Page<MpesaShortCodeDto> findByEnvironment(Environment environment, Pageable pageable);

    /**
     * Find active shortcodes by type and environment
     *
     * @param shortcodeType The shortcode type to filter by
     * @param environment The environment to filter by
     * @return List of active shortcode configurations matching the criteria
     */
    List<MpesaShortCodeDto> findActiveByTypeAndEnvironment(ShortcodeType shortcodeType, Environment environment);

    /**
     * Search shortcodes by business name
     *
     * @param businessName The business name to search for (case-insensitive partial match)
     * @param pageable Pagination parameters
     * @return Page of shortcode configurations matching the business name
     */
    Page<MpesaShortCodeDto> searchByBusinessName(String businessName, Pageable pageable);

    /**
     * Find shortcode valid for the specified amount
     *
     * @param shortcode The shortcode number
     * @param amount The transaction amount to validate
     * @return Optional containing the shortcode if valid for the amount
     */
    Optional<MpesaShortCodeDto> findValidShortcodeForAmount(String shortcode, BigDecimal amount);

    /**
     * Find shortcodes with complex filtering and pagination
     *
     * @param shortcodeType The shortcode type to filter by (optional)
     * @param environment The environment to filter by (optional)
     * @param isActive The active status to filter by (optional)
     * @param businessName The business name to search for (optional, case-insensitive partial match)
     * @param pageable Pagination parameters
     * @return Page of shortcode configurations matching the filters
     */
    Page<MpesaShortCodeDto> findWithFilters(ShortcodeType shortcodeType, Environment environment,
                                            Boolean isActive, String businessName, Pageable pageable);

    /**
     * Find active shortcodes by type with pagination
     *
     * @param shortcodeType The shortcode type to filter by
     * @param pageable Pagination parameters
     * @return Page of active shortcode configurations matching the type
     */
    Page<MpesaShortCodeDto> findActiveByShortcodeType(ShortcodeType shortcodeType, Pageable pageable);

    /**
     * Find active shortcodes by environment with pagination
     *
     * @param environment The environment to filter by
     * @param pageable Pagination parameters
     * @return Page of active shortcode configurations matching the environment
     */
    Page<MpesaShortCodeDto> findActiveByEnvironment(Environment environment, Pageable pageable);

    /**
     * Activate a shortcode
     *
     * @param uuid The UUID of the shortcode to activate
     * @return Updated shortcode configuration
     * @throws IllegalArgumentException if shortcode not found
     */
    MpesaShortCodeDto activate(UUID uuid);

    /**
     * Deactivate a shortcode
     *
     * @param uuid The UUID of the shortcode to deactivate
     * @return Updated shortcode configuration
     * @throws IllegalArgumentException if shortcode not found
     */
    MpesaShortCodeDto deactivate(UUID uuid);

    /**
     * Soft delete a shortcode
     *
     * @param uuid The UUID of the shortcode to delete
     * @throws IllegalArgumentException if shortcode not found
     */
    void delete(UUID uuid);

    /**
     * Restore a soft-deleted shortcode
     *
     * @param uuid The UUID of the shortcode to restore
     * @return Restored shortcode configuration
     * @throws IllegalArgumentException if shortcode not found
     */
    MpesaShortCodeDto restore(UUID uuid);

    /**
     * Check if shortcode exists by shortcode number
     *
     * @param shortcode The shortcode number to check
     * @return true if shortcode exists, false otherwise
     */
    boolean existsByShortcode(String shortcode);

    /**
     * Check if shortcode exists and is active
     *
     * @param shortcode The shortcode number to check
     * @return true if shortcode exists and is active, false otherwise
     */
    boolean existsByShortcodeAndIsActive(String shortcode);

    /**
     * Check if shortcode is active by UUID
     *
     * @param uuid The UUID of the shortcode to check
     * @return true if shortcode is active, false otherwise
     */
    boolean isActive(UUID uuid);

    /**
     * Validate shortcode configuration
     * Checks for required fields, URL formats, amount ranges, etc.
     *
     * @param shortCodeDto The shortcode configuration to validate
     * @return true if configuration is valid, false otherwise
     */
    boolean validateConfiguration(MpesaShortCodeDto shortCodeDto);

    /**
     * Test shortcode connectivity
     * Validates API credentials and connectivity to M-Pesa
     *
     * @param uuid The UUID of the shortcode to test
     * @return true if connectivity test passes, false otherwise
     * @throws IllegalArgumentException if shortcode not found
     */
    boolean testConnectivity(UUID uuid);

    /**
     * Get shortcode statistics
     *
     * @return Statistics object containing counts by type, environment, and status
     */
    ShortcodeStatistics getStatistics();

    /**
     * Get count of active shortcodes by type
     *
     * @param shortcodeType The shortcode type to count
     * @return Number of active shortcodes of the specified type
     */
    Long countActiveByType(ShortcodeType shortcodeType);

    /**
     * Get count of active shortcodes by environment
     *
     * @param environment The environment to count
     * @return Number of active shortcodes in the specified environment
     */
    Long countActiveByEnvironment(Environment environment);

    /**
     * Bulk activate shortcodes
     *
     * @param uuids List of UUIDs to activate
     * @return List of updated shortcode configurations
     */
    List<MpesaShortCodeDto> bulkActivate(List<UUID> uuids);

    /**
     * Bulk deactivate shortcodes
     *
     * @param uuids List of UUIDs to deactivate
     * @return List of updated shortcode configurations
     */
    List<MpesaShortCodeDto> bulkDeactivate(List<UUID> uuids);

    /**
     * Statistics record for shortcode data
     */
    record ShortcodeStatistics(
            Long totalShortcodes,
            Long activeShortcodes,
            Long inactiveShortcodes,
            Long paybillCount,
            Long tillCount,
            Long sandboxCount,
            Long productionCount
    ) {}
}