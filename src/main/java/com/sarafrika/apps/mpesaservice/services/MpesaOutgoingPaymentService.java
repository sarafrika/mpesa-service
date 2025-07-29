package com.sarafrika.apps.mpesaservice.services;

import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MpesaOutgoingPaymentService {

    /**
     * Create a new outgoing payment record
     */
    MpesaOutgoingPayment create(MpesaOutgoingPayment payment);

    /**
     * Update an existing outgoing payment
     */
    MpesaOutgoingPayment update(MpesaOutgoingPayment payment);

    /**
     * Find payment by ID
     */
    Optional<MpesaOutgoingPayment> findById(Long id);

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
     * Find all payments
     */
    List<MpesaOutgoingPayment> findAll();

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
     * Find payments within date range
     */
    List<MpesaOutgoingPayment> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find payments by amount range
     */
    List<MpesaOutgoingPayment> findByAmountRange(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Find payments by command ID
     */
    List<MpesaOutgoingPayment> findByCommandId(String commandId);

    /**
     * Find payments by initiator
     */
    List<MpesaOutgoingPayment> findByInitiatorName(String initiatorName);

    /**
     * Find pending payments
     */
    List<MpesaOutgoingPayment> findPendingPayments();

    /**
     * Find successful payments
     */
    List<MpesaOutgoingPayment> findSuccessfulPayments();

    /**
     * Find failed payments
     */
    List<MpesaOutgoingPayment> findFailedPayments();

    /**
     * Update payment status
     */
    MpesaOutgoingPayment updateStatus(UUID uuid, OutgoingPaymentStatus status);

    /**
     * Mark payment as processed
     */
    MpesaOutgoingPayment markAsProcessed(UUID uuid);

    /**
     * Process B2C payment
     */
    MpesaOutgoingPayment processB2CPayment(UUID shortcodeUuid, String recipientPhone,
                                           BigDecimal amount, String commandId, String remarks);

    /**
     * Process B2B payment
     */
    MpesaOutgoingPayment processB2BPayment(UUID shortcodeUuid, String recipientPhone,
                                           BigDecimal amount, String commandId, String remarks);

    /**
     * Process payment callback
     */
    MpesaOutgoingPayment processCallback(String originatorConversationId, Object callbackData);

    /**
     * Retry failed payment
     */
    MpesaOutgoingPayment retryPayment(UUID uuid);

    /**
     * Cancel pending payment
     */
    MpesaOutgoingPayment cancelPayment(UUID uuid);

    /**
     * Soft delete a payment record
     */
    void delete(UUID uuid);

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
    BigDecimal getTotalAmountByStatus(OutgoingPaymentStatus status);

    /**
     * Get payment count by status
     */
    Long getCountByStatus(OutgoingPaymentStatus status);

    /**
     * Get payments for reconciliation
     */
    List<MpesaOutgoingPayment> getPaymentsForReconciliation(LocalDateTime date);

    /**
     * Get account balance summary
     */
    BigDecimal getTotalUtilityAccountFunds();

    /**
     * Get working account balance summary
     */
    BigDecimal getTotalWorkingAccountFunds();
}