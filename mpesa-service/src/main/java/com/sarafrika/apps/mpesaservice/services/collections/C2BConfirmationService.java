package com.sarafrika.apps.mpesaservice.services.collections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.C2BConfirmationCallback;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.impl.MpesaIncomingPaymentServiceImpl;
import com.sarafrika.apps.mpesaservice.utils.enums.C2BChannel;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Stores each C2B confirmation once, keyed by TransID, and announces it as {@link PaymentEvent#C2B_CONFIRMED}.
 * An unknown BusinessShortCode is still stored (unlinked): the money has moved and Daraja expects an ack. */
@Service
@RequiredArgsConstructor
@Slf4j
public class C2BConfirmationService {

    private static final DateTimeFormatter TRANS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MpesaIncomingPaymentRepository paymentRepository;
    private final MpesaShortCodeRepository shortCodeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /** duplicate is true when this TransID was already stored; nothing is changed or published then. */
    public record Confirmation(MpesaIncomingPayment payment, boolean duplicate) {
    }

    @Transactional
    public Confirmation confirm(C2BConfirmationCallback callback, Map<String, Object> raw) {
        if (!StringUtils.hasText(callback.transId())) {
            throw new IllegalArgumentException("C2B confirmation without a TransID");
        }
        if (callback.transAmount() == null || callback.transAmount().signum() <= 0) {
            throw new IllegalArgumentException("C2B confirmation " + callback.transId() + " without a positive amount");
        }
        String transId = callback.transId().trim();
        Optional<MpesaIncomingPayment> existing = paymentRepository.findByTransactionId(transId);
        if (existing.isPresent()) {
            MpesaIncomingPayment stored = existing.get();
            if (stored.getPaymentType() == IncomingPaymentType.STK_PUSH && stored.getC2bChannel() == null) {
                // The STK callback came first: this confirmation is the same payment, so it only adds detail.
                MpesaIncomingPaymentServiceImpl.copyC2bDetails(fromCallback(callback, transId, null), stored);
                Map<String, Object> merged = new LinkedHashMap<>(
                        stored.getRawCallbackData() == null ? Map.of() : stored.getRawCallbackData());
                merged.put("C2BConfirmation", raw);
                stored.setRawCallbackData(merged);
                paymentRepository.save(stored);
            }
            log.info("C2B confirmation {} was already stored; acknowledging the retry", transId);
            return new Confirmation(stored, true);
        }

        Optional<MpesaShortCode> shortcode = Optional.ofNullable(callback.businessShortCode())
                .map(String::trim).flatMap(shortCodeRepository::findByShortcode);
        if (shortcode.isEmpty()) {
            log.warn("C2B confirmation {} for unknown shortcode {}; storing it unlinked", transId,
                    callback.businessShortCode());
        }

        MpesaIncomingPayment payment = fromCallback(callback, transId, shortcode.orElse(null));
        payment.setTransactionDesc(callback.transType());
        payment.setRawCallbackData(raw != null ? raw
                : objectMapper.convertValue(callback, new TypeReference<Map<String, Object>>() {}));
        payment.setStatus(IncomingPaymentStatus.SUCCESS);
        payment.setResultCode(0);
        payment.setProcessedAt(LocalDateTime.now());
        // saveAndFlush surfaces a concurrent duplicate here, inside this call, as DataIntegrityViolationException.
        MpesaIncomingPayment saved = paymentRepository.saveAndFlush(payment);

        eventPublisher.publishEvent(new PaymentEvent(PaymentEvent.C2B_CONFIRMED, saved.getShortcodeUuid(),
                saved.getUuid(), null, eventData(saved)));
        return new Confirmation(saved, false);
    }

    private static MpesaIncomingPayment fromCallback(C2BConfirmationCallback callback, String transId,
                                                     MpesaShortCode shortcode) {
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setPaymentType(IncomingPaymentType.C2B);
        payment.setTransactionId(transId);
        payment.setShortcodeUuid(shortcode == null ? null : shortcode.getUuid());
        payment.setBusinessShortCode(callback.businessShortCode());
        payment.setC2bChannel(channel(callback.transType(), shortcode));
        payment.setAmount(callback.transAmount());
        payment.setPhoneNumber(callback.msisdn());
        payment.setAccountReference(callback.billRefNumber());
        payment.setInvoiceNumber(blankToNull(callback.invoiceNumber()));
        payment.setOrgAccountBalance(callback.orgAccountBalance());
        payment.setFirstName(blankToNull(callback.firstName()));
        payment.setMiddleName(blankToNull(callback.middleName()));
        payment.setLastName(blankToNull(callback.lastName()));
        payment.setTransactionDate(parseTransTime(callback.transTime()));
        return payment;
    }

    @Transactional(readOnly = true)
    public Optional<MpesaIncomingPayment> findByTransId(String transId) {
        return transId == null ? Optional.empty() : paymentRepository.findByTransactionId(transId.trim());
    }

    static C2BChannel channel(String transType, MpesaShortCode shortcode) {
        C2BChannel fromType = C2BChannel.fromTransType(transType);
        if (fromType != null || shortcode == null) {
            return fromType;
        }
        return shortcode.getShortcodeType() == ShortcodeType.TILL ? C2BChannel.BUY_GOODS : C2BChannel.PAYBILL;
    }

    private static Map<String, Object> eventData(MpesaIncomingPayment payment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trans_id", payment.getTransactionId());
        data.put("business_short_code", payment.getBusinessShortCode());
        data.put("channel", payment.getC2bChannel() == null ? null : payment.getC2bChannel().name());
        data.put("amount", payment.getAmount());
        data.put("account_reference", payment.getAccountReference());
        data.put("msisdn", payment.getPhoneNumber());
        data.put("first_name", payment.getFirstName());
        data.put("last_name", payment.getLastName());
        data.put("trans_time", payment.getTransactionDate() == null ? null : payment.getTransactionDate().toString());
        return data;
    }

    private static LocalDateTime parseTransTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), TRANS_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
