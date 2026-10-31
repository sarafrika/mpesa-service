package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MpesaIncomingPaymentRepository extends JpaRepository<MpesaIncomingPayment, Long> {

    /**
     * Find incoming payment by UUID
     */
    Optional<MpesaIncomingPayment> findByUuid(UUID uuid);

    /**
     * Find incoming payment by M-Pesa transaction ID (receipt)
     */
    Optional<MpesaIncomingPayment> findByTransactionId(String transactionId);

    /**
     * Find incoming payment by STK Push checkout request ID
     */
    Optional<MpesaIncomingPayment> findByCheckoutRequestId(String checkoutRequestId);

    /**
     * Find all payments for a given shortcode
     */
    List<MpesaIncomingPayment> findByShortcodeUuid(UUID shortcodeUuid);

    /**
     * Find all payments originating from a phone number
     */
    List<MpesaIncomingPayment> findByPhoneNumber(String phoneNumber);

    /**
     * Find all payments with a given status
     */
    List<MpesaIncomingPayment> findByStatus(IncomingPaymentStatus status);

    /**
     * Find all payments of a given payment type
     */
    List<MpesaIncomingPayment> findByPaymentType(IncomingPaymentType paymentType);

    /**
     * Find all payments with an account reference (optionally filtered by checkout request)
     */
    List<MpesaIncomingPayment> findByAccountReference(String accountReference);

    /**
     * Find payments created within a date range
     */
    List<MpesaIncomingPayment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find payments whose amount falls within a range
     */
    List<MpesaIncomingPayment> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Check whether a payment already exists for a transaction ID
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Sum the amount of all payments in a given status
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM MpesaIncomingPayment p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") IncomingPaymentStatus status);

    /**
     * Count payments in a given status
     */
    Long countByStatus(IncomingPaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MpesaIncomingPayment p WHERE p.checkoutRequestId = :id")
    Optional<MpesaIncomingPayment> lockByCheckoutRequestId(@Param("id") String checkoutRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MpesaIncomingPayment p WHERE p.transactionId = :id")
    Optional<MpesaIncomingPayment> lockByTransactionId(@Param("id") String transactionId);

    /** Removes a C2B row merged into its STK twin; a soft delete would keep holding the receipt. */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM mpesa_incoming_payments WHERE id = :id", nativeQuery = true)
    void hardDelete(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE webhook_deliveries SET payment_uuid = :to WHERE payment_uuid = :from", nativeQuery = true)
    void repointWebhookDeliveries(@Param("from") UUID from, @Param("to") UUID to);

    /** Claims due STK requests for the status job: SKIP LOCKED keeps two instances off the same rows. */
    @Query(value = """
            SELECT * FROM mpesa_incoming_payments
            WHERE payment_type = 'STK_PUSH' AND status = 'PENDING' AND deleted_at IS NULL
              AND checkout_request_id IS NOT NULL AND shortcode_uuid IS NOT NULL
              AND created_at < :before AND created_at >= :notBefore
              AND (next_check_at IS NULL OR next_check_at <= :now)
            ORDER BY COALESCE(next_check_at, created_at), id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<MpesaIncomingPayment> claimStkChecks(@Param("before") LocalDateTime before,
                                              @Param("notBefore") LocalDateTime notBefore,
                                              @Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Oldest first, for the job that asks Safaricom about STK requests still pending. */
    List<MpesaIncomingPayment> findByPaymentTypeAndStatusAndCreatedAtBefore(
            IncomingPaymentType paymentType, IncomingPaymentStatus status, LocalDateTime before, Pageable pageable);

    /** Successful payments that carried the shortcode's utility balance (C2B OrgAccountBalance), newest first. */
    @Query("""
            SELECT p FROM MpesaIncomingPayment p
            WHERE p.shortcodeUuid = :shortcode AND p.status = :status AND p.orgAccountBalance IS NOT NULL
            ORDER BY COALESCE(p.transactionDate, p.createdAt) DESC, p.id DESC""")
    List<MpesaIncomingPayment> newestWithBalance(@Param("shortcode") UUID shortcodeUuid,
                                                 @Param("status") IncomingPaymentStatus status, Pageable limit);

    /** For each of these shortcodes, its newest successful payment that carried the utility balance. */
    @Query("""
            SELECT p FROM MpesaIncomingPayment p
            WHERE p.shortcodeUuid IN :ids AND p.status = :status AND p.orgAccountBalance IS NOT NULL
              AND COALESCE(p.transactionDate, p.createdAt) = (
                SELECT MAX(COALESCE(p2.transactionDate, p2.createdAt)) FROM MpesaIncomingPayment p2
                WHERE p2.shortcodeUuid = p.shortcodeUuid AND p2.status = :status
                  AND p2.orgAccountBalance IS NOT NULL)
            ORDER BY p.id DESC""")
    List<MpesaIncomingPayment> newestWithBalanceFor(@Param("ids") List<UUID> shortcodeUuids,
                                                    @Param("status") IncomingPaymentStatus status);
}
