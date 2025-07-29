package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentType;
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
public interface MpesaOutgoingPaymentRepository extends JpaRepository<MpesaOutgoingPayment, Long> {

    /**
     * Find payment by UUID
     */
    Optional<MpesaOutgoingPayment> findByUuid(UUID uuid);

    /**
     * Find payment by transaction ID
     */
    Optional<MpesaOutgoingPayment> findByTransactionId(String transactionId);

    /**
     * Find payment by originator conversation ID
     */
    Optional<MpesaOutgoingPayment> findByOriginatorConversationId(String originatorConversationId);

    /**
     * Find payment by conversation ID
     */
    Optional<MpesaOutgoingPayment> findByConversationId(String conversationId);

    /**
     * Find payments by shortcode UUID
     */
    List<MpesaOutgoingPayment> findByShortcodeUuid(UUID shortcodeUuid);

    /**
     * Find payments by recipient phone number
     */
    List<MpesaOutgoingPayment> findByRecipientPhoneNumber(String recipientPhoneNumber);

    /**
     * Find payments by status
     */
    List<MpesaOutgoingPayment> findByStatus(OutgoingPaymentStatus status);

    /**
     * Find payments by payment type
     */
    List<MpesaOutgoingPayment> findByPaymentType(OutgoingPaymentType paymentType);

    /**
     * Find payments by command ID
     */
    List<MpesaOutgoingPayment> findByCommandId(String commandId);

    /**
     * Find payments by initiator name
     */
    List<MpesaOutgoingPayment> findByInitiatorName(String initiatorName);

    /**
     * Find pending payments ordered by creation date
     */
    List<MpesaOutgoingPayment> findByStatusOrderByCreatedAtAsc(OutgoingPaymentStatus status);

    /**
     * Find payments within date range
     */
    List<MpesaOutgoingPayment> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find payments by created date range
     */
    List<MpesaOutgoingPayment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find payments by amount range
     */
    List<MpesaOutgoingPayment> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Find payments by shortcode and status
     */
    List<MpesaOutgoingPayment> findByShortcodeUuidAndStatus(UUID shortcodeUuid, OutgoingPaymentStatus status);

    /**
     * Find payments by recipient and status
     */
    List<MpesaOutgoingPayment> findByRecipientPhoneNumberAndStatus(String recipientPhoneNumber, OutgoingPaymentStatus status);

    /**
     * Find unprocessed payments
     */
    List<MpesaOutgoingPayment> findByProcessedAtIsNull();

    /**
     * Find payments by remarks containing
     */
    List<MpesaOutgoingPayment> findByRemarksContainingIgnoreCase(String remarks);

    /**
     * Find payments by occasion containing
     */
    List<MpesaOutgoingPayment> findByOccasionContainingIgnoreCase(String occasion);

    /**
     * Check if transaction exists
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Check if originator conversation ID exists
     */
    boolean existsByOriginatorConversationId(String originatorConversationId);

    /**
     * Get total amount by status
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM MpesaOutgoingPayment p WHERE p.status = :status")
    BigDecimal getTotalAmountByStatus(@Param("status") OutgoingPaymentStatus status);

    /**
     * Get payment count by status
     */
    Long countByStatus(OutgoingPaymentStatus status);

    /**
     * Get payment count by payment type
     */
    Long countByPaymentType(OutgoingPaymentType paymentType);

    /**
     * Get total amount by shortcode and status
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM MpesaOutgoingPayment p " +
            "WHERE p.shortcodeUuid = :shortcodeUuid AND p.status = :status")
    BigDecimal getTotalAmountByShortcodeAndStatus(@Param("shortcodeUuid") UUID shortcodeUuid,
                                                  @Param("status") OutgoingPaymentStatus status);

    /**
     * Find recent payments by recipient phone
     */
    @Query("SELECT p FROM MpesaOutgoingPayment p WHERE p.recipientPhoneNumber = :phoneNumber " +
            "ORDER BY p.createdAt DESC")
    List<MpesaOutgoingPayment> findRecentPaymentsByRecipient(@Param("phoneNumber") String phoneNumber);

    /**
     * Find payments by result code
     */
    List<MpesaOutgoingPayment> findByResultCode(Integer resultCode);

    /**
     * Find failed payments with specific result codes
     */
    @Query("SELECT p FROM MpesaOutgoingPayment p WHERE p.status = 'FAILED' " +
            "AND p.resultCode IN :resultCodes ORDER BY p.createdAt DESC")
    List<MpesaOutgoingPayment> findFailedPaymentsByResultCodes(@Param("resultCodes") List<Integer> resultCodes);

    /**
     * Get payments for reconciliation by date
     */
    @Query("SELECT p FROM MpesaOutgoingPayment p WHERE DATE(p.transactionDate) = DATE(:date) " +
            "AND p.status = :status ORDER BY p.transactionDate")
    List<MpesaOutgoingPayment> findPaymentsForReconciliation(@Param("date") LocalDateTime date,
                                                             @Param("status") OutgoingPaymentStatus status);

    /**
     * Get total utility account funds
     */
    @Query("SELECT COALESCE(AVG(p.utilityAccountAvailableFunds), 0) FROM MpesaOutgoingPayment p " +
            "WHERE p.utilityAccountAvailableFunds IS NOT NULL AND p.status = 'SUCCESS' " +
            "AND p.transactionDate >= :fromDate ORDER BY p.transactionDate DESC")
    BigDecimal getLatestUtilityAccountFunds(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Get total working account funds
     */
    @Query("SELECT COALESCE(AVG(p.workingAccountAvailableFunds), 0) FROM MpesaOutgoingPayment p " +
            "WHERE p.workingAccountAvailableFunds IS NOT NULL AND p.status = 'SUCCESS' " +
            "AND p.transactionDate >= :fromDate ORDER BY p.transactionDate DESC")
    BigDecimal getLatestWorkingAccountFunds(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Get daily transaction summary
     */
    @Query("SELECT p.status, p.paymentType, COUNT(p), COALESCE(SUM(p.amount), 0) FROM MpesaOutgoingPayment p " +
            "WHERE DATE(p.transactionDate) = DATE(:date) GROUP BY p.status, p.paymentType")
    List<Object[]> getDailyTransactionSummary(@Param("date") LocalDateTime date);

    /**
     * Find retryable failed payments
     */
    @Query("SELECT p FROM MpesaOutgoingPayment p WHERE p.status = 'FAILED' " +
            "AND p.resultCode NOT IN :nonRetryableCodes " +
            "AND p.createdAt >= :cutoffDate ORDER BY p.createdAt")
    List<MpesaOutgoingPayment> findRetryableFailedPayments(@Param("nonRetryableCodes") List<Integer> nonRetryableCodes,
                                                           @Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find payments by initiator and date range
     */
    @Query("SELECT p FROM MpesaOutgoingPayment p WHERE p.initiatorName = :initiator " +
            "AND p.createdAt BETWEEN :startDate AND :endDate ORDER BY p.createdAt DESC")
    List<MpesaOutgoingPayment> findByInitiatorAndDateRange(@Param("initiator") String initiator,
                                                           @Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);
}