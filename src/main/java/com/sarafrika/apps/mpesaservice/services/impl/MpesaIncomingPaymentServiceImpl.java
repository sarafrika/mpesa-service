package com.sarafrika.apps.mpesaservice.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link MpesaIncomingPaymentService}.
 * <p>
 * Persists and tracks the lifecycle of inbound M-Pesa payments (STK Push and C2B)
 * across any number of configured shortcodes. The implementation is intentionally
 * generic and shortcode-driven: nothing here is bound to a specific till, paybill or
 * consuming business.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MpesaIncomingPaymentServiceImpl implements MpesaIncomingPaymentService {

    private final MpesaIncomingPaymentRepository repository;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter MPESA_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ==================== CRUD ====================

    @Override
    public MpesaIncomingPayment create(MpesaIncomingPayment payment) {
        log.info("Creating incoming payment for shortcode: {}, checkoutRequestId: {}",
                payment.getShortcodeUuid(), payment.getCheckoutRequestId());
        return repository.save(payment);
    }

    @Override
    public MpesaIncomingPayment update(MpesaIncomingPayment payment) {
        log.debug("Updating incoming payment: {}", payment.getUuid());
        return repository.save(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MpesaIncomingPayment> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MpesaIncomingPayment> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MpesaIncomingPayment> findByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MpesaIncomingPayment> findByCheckoutRequestId(String checkoutRequestId) {
        return repository.findByCheckoutRequestId(checkoutRequestId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findByShortcodeUuid(UUID shortcodeUuid) {
        return repository.findByShortcodeUuid(shortcodeUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findByPhoneNumber(String phoneNumber) {
        return repository.findByPhoneNumber(phoneNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findByAccountReference(String accountReference) {
        return repository.findByAccountReference(accountReference);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findByStatus(IncomingPaymentStatus status) {
        return repository.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findByPaymentType(IncomingPaymentType paymentType) {
        return repository.findByPaymentType(paymentType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findByCreatedAtBetween(startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findByAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
        return repository.findByAmountBetween(minAmount, maxAmount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findPendingPayments() {
        return repository.findByStatus(IncomingPaymentStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findSuccessfulPayments() {
        return repository.findByStatus(IncomingPaymentStatus.SUCCESS);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> findFailedPayments() {
        return repository.findByStatus(IncomingPaymentStatus.FAILED);
    }

    // ==================== STATUS TRANSITIONS ====================

    @Override
    public MpesaIncomingPayment updateStatus(UUID uuid, IncomingPaymentStatus status) {
        MpesaIncomingPayment payment = getByUuidOrThrow(uuid);
        payment.setStatus(status);
        return repository.save(payment);
    }

    @Override
    public MpesaIncomingPayment markAsProcessed(UUID uuid) {
        MpesaIncomingPayment payment = getByUuidOrThrow(uuid);
        payment.setProcessedAt(LocalDateTime.now());
        return repository.save(payment);
    }

    @Override
    public MpesaIncomingPayment processStkPushCallback(String checkoutRequestId, Object callbackData) {
        log.info("Processing STK Push callback for checkoutRequestId: {}", checkoutRequestId);

        MpesaIncomingPayment payment = repository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No incoming payment found for checkoutRequestId: " + checkoutRequestId));

        Map<String, Object> raw = toMap(callbackData);
        payment.setRawCallbackData(raw);

        Integer resultCode = readInteger(raw.get("ResultCode"));
        payment.setResultCode(resultCode);
        payment.setResultDesc(readString(raw.get("ResultDesc")));

        Map<String, Object> metadata = extractStkMetadata(raw);
        if (metadata != null) {
            String receipt = readString(metadata.get("MpesaReceiptNumber"));
            if (receipt != null) {
                payment.setTransactionId(receipt);
            }
            BigDecimal amount = readBigDecimal(metadata.get("Amount"));
            if (amount != null) {
                payment.setAmount(amount);
            }
            String phone = readString(metadata.get("PhoneNumber"));
            if (phone != null) {
                payment.setPhoneNumber(phone);
            }
            LocalDateTime txnDate = parseMpesaTimestamp(metadata.get("TransactionDate"));
            if (txnDate != null) {
                payment.setTransactionDate(txnDate);
            }
        }

        payment.setStatus(resolveStatus(resultCode));
        payment.setProcessedAt(LocalDateTime.now());

        MpesaIncomingPayment saved = repository.save(payment);
        log.info("STK Push callback processed for checkoutRequestId: {} -> status: {}",
                checkoutRequestId, saved.getStatus());
        return saved;
    }

    @Override
    public MpesaIncomingPayment processC2BCallback(String transactionId, Object callbackData) {
        log.info("Processing C2B callback for transactionId: {}", transactionId);

        Map<String, Object> raw = toMap(callbackData);

        MpesaIncomingPayment payment = repository.findByTransactionId(transactionId)
                .orElseGet(MpesaIncomingPayment::new);

        payment.setPaymentType(IncomingPaymentType.C2B);
        payment.setTransactionId(transactionId);
        payment.setRawCallbackData(raw);

        applyIfPresent(readBigDecimal(raw.get("TransAmount")), payment::setAmount);
        applyIfPresent(readString(raw.get("MSISDN")), payment::setPhoneNumber);
        applyIfPresent(readString(raw.get("BillRefNumber")), payment::setAccountReference);
        applyIfPresent(readString(raw.get("FirstName")), payment::setFirstName);
        applyIfPresent(readString(raw.get("MiddleName")), payment::setMiddleName);
        applyIfPresent(readString(raw.get("LastName")), payment::setLastName);
        LocalDateTime txnDate = parseMpesaTimestamp(raw.get("TransTime"));
        if (txnDate != null) {
            payment.setTransactionDate(txnDate);
        }

        payment.setStatus(IncomingPaymentStatus.SUCCESS);
        payment.setProcessedAt(LocalDateTime.now());

        return repository.save(payment);
    }

    @Override
    public void delete(UUID uuid) {
        MpesaIncomingPayment payment = getByUuidOrThrow(uuid);
        repository.delete(payment); // soft delete via @SQLDelete on BaseEntity
    }

    // ==================== REPORTING / RECONCILIATION ====================

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTransactionId(String transactionId) {
        return repository.existsByTransactionId(transactionId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalAmountByStatus(IncomingPaymentStatus status) {
        BigDecimal total = repository.sumAmountByStatus(status);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public Long getCountByStatus(IncomingPaymentStatus status) {
        return repository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaIncomingPayment> getPaymentsForReconciliation(LocalDateTime date) {
        LocalDate day = date.toLocalDate();
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        return repository.findByCreatedAtBetween(start, end);
    }

    // ==================== HELPERS ====================

    private MpesaIncomingPayment getByUuidOrThrow(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Incoming payment not found: " + uuid));
    }

    private IncomingPaymentStatus resolveStatus(Integer resultCode) {
        if (resultCode == null) {
            return IncomingPaymentStatus.FAILED;
        }
        if (resultCode == 0) {
            return IncomingPaymentStatus.SUCCESS;
        }
        // 1032 is the M-Pesa result code for a request cancelled by the user
        if (resultCode == 1032) {
            return IncomingPaymentStatus.CANCELLED;
        }
        return IncomingPaymentStatus.FAILED;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object callbackData) {
        if (callbackData == null) {
            return Map.of();
        }
        if (callbackData instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return objectMapper.convertValue(callbackData, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Extracts the STK Push callback metadata (keyed by item Name) from a raw callback map.
     * Handles the Daraja shape: {@code CallbackMetadata: { Item: [ { Name, Value }, ... ] }}.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractStkMetadata(Map<String, Object> raw) {
        Object metadataObj = raw.get("CallbackMetadata");
        if (!(metadataObj instanceof Map<?, ?> metadataMap)) {
            return null;
        }
        Object itemsObj = ((Map<String, Object>) metadataMap).get("Item");
        if (!(itemsObj instanceof List<?> items)) {
            return null;
        }
        java.util.HashMap<String, Object> flattened = new java.util.HashMap<>();
        for (Object itemObj : items) {
            if (itemObj instanceof Map<?, ?> item) {
                Object name = ((Map<String, Object>) item).get("Name");
                Object value = ((Map<String, Object>) item).get("Value");
                if (name != null) {
                    flattened.put(name.toString(), value);
                }
            }
        }
        return flattened;
    }

    private <T> void applyIfPresent(T value, java.util.function.Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private Integer readInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readString(Object value) {
        return value != null ? value.toString() : null;
    }

    private LocalDateTime parseMpesaTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, MPESA_TIMESTAMP_FORMATTER);
        } catch (Exception e) {
            log.debug("Unable to parse M-Pesa timestamp: {}", raw);
            return null;
        }
    }
}
