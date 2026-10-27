package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    Optional<MpesaShortCode> findByCallbackToken(String callbackToken);

    Optional<MpesaShortCode> findFirstByShortcodeTypeAndStoreNumber(ShortcodeType type, String storeNumber);

    /** The shortcode Safaricom means by a number in a callback: the shortcode itself, or a till's store number. */
    default Optional<MpesaShortCode> findByNumber(String number) {
        if (number == null || number.isBlank()) {
            return Optional.empty();
        }
        String trimmed = number.trim();
        return findByShortcode(trimmed)
                .or(() -> findFirstByShortcodeTypeAndStoreNumber(ShortcodeType.TILL, trimmed));
    }

    /** Tills whose store number is still the till number and nobody has confirmed it. */
    default List<MpesaShortCode> findTillsWithUnconfirmedStoreNumber() {
        return findUnconfirmedStoreNumbers(ShortcodeType.TILL);
    }

    @Query("SELECT s FROM MpesaShortCode s WHERE s.shortcodeType = :type AND s.storeNumber = s.shortcode "
            + "AND s.storeNumberConfirmedAt IS NULL")
    List<MpesaShortCode> findUnconfirmedStoreNumbers(@org.springframework.data.repository.query.Param("type")
                                                     ShortcodeType type);

    /**
     * Find all active shortcode configurations with pagination
     */
    Page<MpesaShortCode> findByIsActiveTrue(Pageable pageable);

    /**
     * Check if shortcode exists
     */
    boolean existsByShortcode(String shortcode);
}