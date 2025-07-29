package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Find payment by UUID
     */
    Optional<MpesaIncomingPayment> findByUuid(UUID uuid);

    /**
     * Find payment by transaction ID
     */
    Optional<MpesaIncomingPayment> findByTransactionId(String transactionId);

    /**
     * Find payment by checkout request ID
     */
    Optional<MpesaIncomingPayment> findByCheckoutRequestId(String checkoutRequestId);

    /**
     * Find payment by merchant request ID
     */
    Optional<MpesaIncomingPayment> findByMerchantRequestId(String merchantRequestId);

    /**
     * Find payments by shortcode UUID
     */
    List<MpesaIncomingPayment> findByShortcodeUuid(UUID shortcodeUuid);

    /**
     * Find payments by phone number
     */
    List<MpesaIncomingPayment> findByPhoneNumber(String phoneNumber);

    /**
     * Find payments by status
     */
    List<MpesaIncomingPayment> findByStatus(IncomingPaymentStatus status);

    /**
     * Find payments by payment type
     */
    List<MpesaIncomingPayment> findByPaymentType(IncomingPaymentType paymentType);

    /**
     * Find pending payments
     */
    List<MpesaIncomingPayment> findByStatusOrderByCreatedAtAsc(IncomingPaymentStatus status);

    /**
     * Find payments within date range
     */
    List<MpesaIncomingPayment> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find payments by created date range
     */
    List<MpesaIncomingPayment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find payments by amount range
     */
    List<MpesaIncomingPayment> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Find payments by shortcode and status
     */
    List<MpesaIncomingPayment> findByShortcodeUuidAndStatus(UUID shortcodeUuid, IncomingPaymentStatus status);

    /**
     * Find payments by phone number and status
     */
    List<MpesaIncomingPayment> findByPhoneNumberAndStatus(String phoneNumber, IncomingPaymentStatus status);

    /**
     * Find unprocessed payments
     */
    List<MpesaIncomingPayment> findByProcessedAtIsNull();

    /**
     * Find payments by account reference
     */
    List<MpesaIncomingPayment> findByAccountReference(String accountReference);

    /**
     * Check if transaction exists
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Check if checkout request exists
     */
    boolean existsByCheckoutRequestId(String checkoutRequestId);

    /**
     * Get total amount by status
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM MpesaIncomingPayment p WHERE p.status = :status")
    BigDecimal getTotalAmountByStatus(@Param("status") IncomingPaymentStatus status);

    /**
     * Get payment count by status
     */
    Long countByStatus(IncomingPaymentStatus status);

    /**
     * Get payment count by payment type
     */
    Long countByPaymentType(IncomingPaymentType paymentType);

    /**
     * Get payments for reconciliation by date
     */
    @Query("SELECT p FROM MpesaIncomingPayment p WHERE DATE(p.transactionDate) = DATE(:date) " +
            "AND p.status = :status ORDER BY p.transactionDate")
    List<MpesaIncomingPayment> findPaymentsForReconciliation(@Param("date") LocalDateTime date,
                                                             @Param("status") IncomingPaymentStatus status);

    /**
     * Get total amount by shortcode and status
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM MpesaIncomingPayment p " +
            "WHERE p.shortcodeUuid = :shortcodeUuid AND p.status = :status")
    BigDecimal getTotalAmountByShortcodeAndStatus(@Param("shortcodeUuid") UUID shortcodeUuid,
                                                  @Param("status") IncomingPaymentStatus status);

    /**
     * Find recent payments by phone number
     */
    @Query("SELECT p FROM MpesaIncomingPayment p WHERE p.phoneNumber = :phoneNumber " +
            "ORDER BY p.createdAt DESC")
    List<MpesaIncomingPayment> findRecentPaymentsByPhone(@Param("phoneNumber") String phoneNumber);

    /**
     * Find payments by result code
     */
    List<MpesaIncomingPayment> findByResultCode(Integer resultCode);

    /**
     * Find failed payments with specific result codes
     */
    @Query("SELECT p FROM MpesaIncomingPayment p WHERE p.status = 'FAILED' " +
            "AND p.resultCode IN :resultCodes ORDER BY p.createdAt DESC")
    List<MpesaIncomingPayment> findFailedPaymentsByResultCodes(@Param("resultCodes") List<Integer> resultCodes);

    /**
     * Get daily transaction summary
     */
    @Query("SELECT p.status, COUNT(p), COALESCE(SUM(p.amount), 0) FROM MpesaIncomingPayment p " +
            "WHERE DATE(p.transactionDate) = DATE(:date) GROUP BY p.status")
    List<Object[]> getDailyTransactionSummary(@Param("date") LocalDateTime date);
}