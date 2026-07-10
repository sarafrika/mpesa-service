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
}
