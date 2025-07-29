package com.sarafrika.apps.mpesaservice.services;

import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MpesaIncomingPaymentService {

    /**
     * Create a new incoming payment record
     */
    MpesaIncomingPayment create(MpesaIncomingPayment payment);

    /**
     * Update an existing incoming payment
     */
    MpesaIncomingPayment update(MpesaIncomingPayment payment);

    /**
     * Find payment by ID
     */
    Optional<MpesaIncomingPayment> findById(Long id);

    /**
     * Find payment by UUID
     */
    Optional<MpesaIncomingPayment> findByUuid(UUID uuid);

    /**
     * Find payment by transaction ID
     */
    Optional<MpesaIncomingPayment> findByTransactionId(String transactionId);

    /**
     * Find payment by checkout request ID (for STK Push)
     */
    Optional<MpesaIncomingPayment> findByCheckoutRequestId(String checkoutRequestId);

    /**
     * Find all payments
     */
    List<MpesaIncomingPayment> findAll();

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
     * Find payments within date range
     */
    List<MpesaIncomingPayment> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find payments by amount range
     */
    List<MpesaIncomingPayment> findByAmountRange(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Find pending payments
     */
    List<MpesaIncomingPayment> findPendingPayments();

    /**
     * Find successful payments
     */
    List<MpesaIncomingPayment> findSuccessfulPayments();

    /**
     * Find failed payments
     */
    List<MpesaIncomingPayment> findFailedPayments();

    /**
     * Update payment status
     */
    MpesaIncomingPayment updateStatus(UUID uuid, IncomingPaymentStatus status);

    /**
     * Mark payment as processed
     */
    MpesaIncomingPayment markAsProcessed(UUID uuid);

    /**
     * Process STK Push callback
     */
    MpesaIncomingPayment processStkPushCallback(String checkoutRequestId, Object callbackData);

    /**
     * Process C2B callback
     */
    MpesaIncomingPayment processC2BCallback(String transactionId, Object callbackData);

    /**
     * Soft delete a payment record
     */
    void delete(UUID uuid);

    /**
     * Check if transaction exists
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Get total amount by status
     */
    BigDecimal getTotalAmountByStatus(IncomingPaymentStatus status);

    /**
     * Get payment count by status
     */
    Long getCountByStatus(IncomingPaymentStatus status);

    /**
     * Get payments for reconciliation
     */
    List<MpesaIncomingPayment> getPaymentsForReconciliation(LocalDateTime date);
}