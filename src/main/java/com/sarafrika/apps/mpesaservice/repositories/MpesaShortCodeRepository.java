package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MpesaShortCodeRepository extends JpaRepository<MpesaShortCode, Long> {

    /**
     * Find shortcode by UUID
     */
    Optional<MpesaShortCode> findByUuid(UUID uuid);

    /**
     * Find shortcode by shortcode number
     */
    Optional<MpesaShortCode> findByShortcode(String shortcode);

    /**
     * Find all active shortcodes with pagination
     */
    Page<MpesaShortCode> findByIsActiveTrue(Pageable pageable);

    /**
     * Find all active shortcodes (list version for specific use cases)
     */
    List<MpesaShortCode> findByIsActiveTrue();

    /**
     * Find shortcodes by type with pagination
     */
    Page<MpesaShortCode> findByShortcodeType(ShortcodeType shortcodeType, Pageable pageable);

    /**
     * Find shortcodes by type (list version for specific use cases)
     */
    List<MpesaShortCode> findByShortcodeType(ShortcodeType shortcodeType);

    /**
     * Find shortcodes by environment with pagination
     */
    Page<MpesaShortCode> findByEnvironment(Environment environment, Pageable pageable);

    /**
     * Find shortcodes by environment (list version for specific use cases)
     */
    List<MpesaShortCode> findByEnvironment(Environment environment);

    /**
     * Find active shortcodes by type with pagination
     */
    Page<MpesaShortCode> findByShortcodeTypeAndIsActiveTrue(ShortcodeType shortcodeType, Pageable pageable);

    /**
     * Find active shortcodes by type (list version)
     */
    List<MpesaShortCode> findByShortcodeTypeAndIsActiveTrue(ShortcodeType shortcodeType);

    /**
     * Find active shortcodes by environment with pagination
     */
    Page<MpesaShortCode> findByEnvironmentAndIsActiveTrue(Environment environment, Pageable pageable);

    /**
     * Find active shortcodes by environment (list version)
     */
    List<MpesaShortCode> findByEnvironmentAndIsActiveTrue(Environment environment);

    /**
     * Find active shortcodes by type and environment
     */
    List<MpesaShortCode> findByShortcodeTypeAndEnvironmentAndIsActiveTrue(
            ShortcodeType shortcodeType, Environment environment);

    /**
     * Check if shortcode exists
     */
    boolean existsByShortcode(String shortcode);

    /**
     * Check if shortcode exists and is active
     */
    boolean existsByShortcodeAndIsActiveTrue(String shortcode);

    /**
     * Find shortcodes by business name with pagination
     */
    Page<MpesaShortCode> findByBusinessNameContainingIgnoreCase(String businessName, Pageable pageable);

    /**
     * Find shortcodes by business name (list version)
     */
    List<MpesaShortCode> findByBusinessNameContainingIgnoreCase(String businessName);

    /**
     * Find shortcodes by created by with pagination
     */
    Page<MpesaShortCode> findByCreatedBy(String createdBy, Pageable pageable);

    /**
     * Find shortcodes by created by (list version)
     */
    List<MpesaShortCode> findByCreatedBy(String createdBy);

    /**
     * Custom query to find shortcode with minimum and maximum amount validation
     */
    @Query("SELECT s FROM MpesaShortCode s WHERE s.shortcode = :shortcode " +
            "AND s.isActive = true AND :amount BETWEEN s.minAmount AND s.maxAmount")
    Optional<MpesaShortCode> findValidShortcodeForAmount(@Param("shortcode") String shortcode,
                                                         @Param("amount") java.math.BigDecimal amount);

    /**
     * Count active shortcodes by type
     */
    @Query("SELECT COUNT(s) FROM MpesaShortCode s WHERE s.shortcodeType = :type AND s.isActive = true")
    Long countActiveByType(@Param("type") ShortcodeType shortcodeType);

    /**
     * Count active shortcodes by environment
     */
    @Query("SELECT COUNT(s) FROM MpesaShortCode s WHERE s.environment = :env AND s.isActive = true")
    Long countActiveByEnvironment(@Param("env") Environment environment);

    /**
     * Find shortcodes with complex filtering and pagination
     */
    @Query("SELECT s FROM MpesaShortCode s WHERE " +
            "(:shortcodeType IS NULL OR s.shortcodeType = :shortcodeType) AND " +
            "(:environment IS NULL OR s.environment = :environment) AND " +
            "(:isActive IS NULL OR s.isActive = :isActive) AND " +
            "(:businessName IS NULL OR LOWER(s.businessName) LIKE LOWER(CONCAT('%', :businessName, '%')))")
    Page<MpesaShortCode> findWithFilters(@Param("shortcodeType") ShortcodeType shortcodeType,
                                         @Param("environment") Environment environment,
                                         @Param("isActive") Boolean isActive,
                                         @Param("businessName") String businessName,
                                         Pageable pageable);
}