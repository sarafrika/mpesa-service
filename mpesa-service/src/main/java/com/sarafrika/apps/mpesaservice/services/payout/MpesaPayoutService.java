package com.sarafrika.apps.mpesaservice.services.payout;

import com.sarafrika.apps.mpesaservice.dtos.B2CPaymentResponse;
import com.sarafrika.apps.mpesaservice.dtos.B2CPayoutRequest;
import com.sarafrika.apps.mpesaservice.dtos.B2CResult;
import com.sarafrika.apps.mpesaservice.dtos.B2CResultParameters;
import com.sarafrika.apps.mpesaservice.dtos.B2CTimeoutCallback;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.PayoutFloatDto;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaOutgoingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * B2C payouts for other Sarafrika services.
 * <p>
 * A payout is saved as PENDING before Safaricom is called, with its own uuid as the
 * OriginatorConversationID, so a lost reply never loses track of money. The caller's
 * client_reference is unique per client: asking again returns the first payout. Safaricom's result
 * callback settles it once; later or duplicate callbacks change nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaPayoutService {

    private static final Pattern MSISDN = Pattern.compile("254[17]\\d{8}");
    private static final DateTimeFormatter SAFARICOM_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final MpesaOutgoingPaymentRepository paymentRepository;
    private final MpesaShortCodeRepository shortCodeRepository;
    private final MpesaDarajaService darajaService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher events;

    /** Starts a payout, or returns the one already started for this client and reference. */
    public MpesaOutgoingPayment initiate(String clientId, B2CPayoutRequest request) {
        Optional<MpesaOutgoingPayment> existing =
                paymentRepository.findByClientIdAndClientReference(clientId, request.clientReference());
        if (existing.isPresent()) {
            return existing.get();
        }
        MpesaShortCode shortcode = shortCodeRepository.findByUuid(request.shortcodeUuid())
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found: " + request.shortcodeUuid()));
        if (!Boolean.TRUE.equals(shortcode.getIsActive())) {
            throw new IllegalStateException("Shortcode " + shortcode.getShortcode() + " is suspended; payouts are refused");
        }
        validate(shortcode, request);

        MpesaOutgoingPayment payment;
        try {
            payment = transactionTemplate.execute(status -> paymentRepository.saveAndFlush(pending(clientId, shortcode, request)));
        } catch (DataIntegrityViolationException ex) {
            // Another request with the same reference won the race; return that one.
            return paymentRepository.findByClientIdAndClientReference(clientId, request.clientReference()).orElseThrow(() -> ex);
        }

        MpesaApiResponse<B2CPaymentResponse> response = darajaService.sendB2CPayout(shortcode,
                payment.getOriginatorConversationId(), request.phoneNumber(), request.amount(), request.remarks(),
                request.occasion());
        UUID uuid = payment.getUuid();
        return transactionTemplate.execute(status -> {
            MpesaOutgoingPayment saved = paymentRepository.findByUuid(uuid).orElseThrow();
            if (response.success() && response.data() != null && response.data().isAccepted()) {
                saved.setConversationId(response.data().conversationId());
                saved.setResultDesc(response.data().responseDescription());
            } else {
                saved.setStatus(OutgoingPaymentStatus.FAILED);
                saved.setResultDesc(response.error() != null ? response.error().errorMessage()
                        : response.data() != null ? response.data().responseDescription() : "Safaricom refused the request");
                saved.setProcessedAt(LocalDateTime.now());
            }
            return paymentRepository.save(saved);
        });
    }

    /** Settles a payout from Safaricom's result; only the first final result counts. */
    @Transactional
    public Optional<MpesaOutgoingPayment> applyResult(B2CResult result, String raw) {
        Optional<MpesaOutgoingPayment> match = find(result.originatorConversationId(), result.conversationId());
        if (match.isEmpty()) {
            log.warn("B2C result for unknown payout {} / {}", result.originatorConversationId(), result.conversationId());
            return Optional.empty();
        }
        MpesaOutgoingPayment payment = match.get();
        if (payment.getStatus() != OutgoingPaymentStatus.PENDING) {
            log.info("Ignoring repeat B2C result for payout {} already {}", payment.getUuid(), payment.getStatus());
            return match;
        }
        payment.setResultCode(result.resultCode());
        payment.setResultDesc(result.resultDesc());
        payment.setConversationId(result.conversationId());
        payment.setRawCallbackData(raw);
        payment.setProcessedAt(LocalDateTime.now());
        if (result.isSuccessful()) {
            B2CResultParameters params = result.resultParameters();
            payment.setStatus(OutgoingPaymentStatus.SUCCESS);
            payment.setTransactionId(params != null && params.getTransactionReceipt() != null
                    ? params.getTransactionReceipt() : result.transactionId());
            if (params != null) {
                payment.setRecipientName(recipientName(params.getReceiverPartyPublicName()));
                payment.setChargesPaid(decimal(params.getB2CChargesPaidAmount()));
                payment.setUtilityAccountAvailableFunds(decimal(params.getB2CUtilityAccountAvailableFunds()));
                payment.setWorkingAccountAvailableFunds(decimal(params.getB2CWorkingAccountAvailableFunds()));
                payment.setTransactionDate(time(params.getTransactionCompletedDateTime()));
            }
        } else {
            payment.setStatus(OutgoingPaymentStatus.FAILED);
        }
        MpesaOutgoingPayment saved = paymentRepository.save(payment);
        events.publishEvent(settledEvent(saved));
        return Optional.of(saved);
    }

    /** b2c.completed or b2c.failed for the paying client; webhooks go out after this transaction commits. */
    static PaymentEvent settledEvent(MpesaOutgoingPayment payment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payout_uuid", payment.getUuid());
        data.put("client_reference", payment.getClientReference());
        data.put("status", payment.getStatus().name());
        data.put("amount", payment.getAmount());
        data.put("phone_number", payment.getRecipientPhoneNumber());
        data.put("transaction_id", payment.getTransactionId());
        data.put("recipient_name", payment.getRecipientName());
        data.put("charges_paid", payment.getChargesPaid());
        data.put("result_code", payment.getResultCode());
        data.put("result_desc", payment.getResultDesc());
        data.put("transaction_date", payment.getTransactionDate() == null ? null : payment.getTransactionDate().toString());
        String type = payment.getStatus() == OutgoingPaymentStatus.SUCCESS ? PaymentEvent.B2C_COMPLETED
                : PaymentEvent.B2C_FAILED;
        return new PaymentEvent(type, payment.getShortcodeUuid(), payment.getUuid(), payment.getClientId(), data);
    }

    /** A queue timeout leaves the outcome unknown, so the payout stays PENDING and is flagged. */
    @Transactional
    public Optional<MpesaOutgoingPayment> applyTimeout(B2CTimeoutCallback timeout) {
        return find(timeout.originatorConversationId(), timeout.conversationId()).map(payment -> {
            if (payment.getStatus() == OutgoingPaymentStatus.PENDING && payment.getTimedOutAt() == null) {
                payment.setTimedOutAt(LocalDateTime.now());
                payment.setResultDesc(timeout.resultDesc());
                return paymentRepository.save(payment);
            }
            return payment;
        });
    }

    @Transactional(readOnly = true)
    public Optional<MpesaOutgoingPayment> findForClient(String clientId, UUID uuid) {
        return paymentRepository.findByUuidAndClientId(uuid, clientId);
    }

    @Transactional(readOnly = true)
    public Optional<MpesaOutgoingPayment> findForClient(String clientId, String clientReference) {
        return paymentRepository.findByClientIdAndClientReference(clientId, clientReference);
    }

    @Transactional(readOnly = true)
    public PayoutFloatDto latestFloat(UUID shortcodeUuid) {
        return paymentRepository
                .findFirstByShortcodeUuidAndStatusAndUtilityAccountAvailableFundsIsNotNullOrderByProcessedAtDesc(
                        shortcodeUuid, OutgoingPaymentStatus.SUCCESS)
                .map(p -> new PayoutFloatDto(shortcodeUuid, p.getUtilityAccountAvailableFunds(),
                        p.getWorkingAccountAvailableFunds(), p.getProcessedAt()))
                .orElseGet(() -> new PayoutFloatDto(shortcodeUuid, null, null, null));
    }

    private Optional<MpesaOutgoingPayment> find(String originatorConversationId, String conversationId) {
        Optional<MpesaOutgoingPayment> byOriginator = originatorConversationId == null ? Optional.empty()
                : paymentRepository.findByOriginatorConversationId(originatorConversationId);
        return byOriginator.isPresent() || conversationId == null ? byOriginator
                : paymentRepository.findByConversationId(conversationId);
    }

    private static void validate(MpesaShortCode shortcode, B2CPayoutRequest request) {
        if (!MSISDN.matcher(request.phoneNumber()).matches()) {
            throw new IllegalArgumentException("phone_number must look like 2547XXXXXXXX or 2541XXXXXXXX");
        }
        if (request.amount().stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("M-Pesa pays whole shillings only");
        }
        if (shortcode.getMinAmount() != null && request.amount().compareTo(shortcode.getMinAmount()) < 0
                || shortcode.getMaxAmount() != null && request.amount().compareTo(shortcode.getMaxAmount()) > 0) {
            throw new IllegalArgumentException("Amount is outside this shortcode's limits");
        }
        if (shortcode.getInitiatorName() == null || shortcode.getInitiatorName().isBlank()) {
            throw new IllegalStateException("Shortcode " + shortcode.getShortcode() + " has no B2C initiator configured");
        }
    }

    private static MpesaOutgoingPayment pending(String clientId, MpesaShortCode shortcode, B2CPayoutRequest request) {
        MpesaOutgoingPayment payment = new MpesaOutgoingPayment();
        payment.setOriginatorConversationId("sar-" + UUID.randomUUID());
        payment.setShortcodeUuid(shortcode.getUuid());
        payment.setPaymentType(OutgoingPaymentType.B2C);
        payment.setRecipientPhoneNumber(request.phoneNumber());
        payment.setAmount(request.amount());
        payment.setCommandId("BusinessPayment");
        payment.setInitiatorName(shortcode.getInitiatorName());
        payment.setRemarks(request.remarks());
        payment.setOccasion(request.occasion());
        payment.setStatus(OutgoingPaymentStatus.PENDING);
        payment.setClientId(clientId);
        payment.setClientReference(request.clientReference());
        return payment;
    }

    /** Safaricom sends "254712345678 - JANE WANJIRU KAMAU"; keep the name. */
    static String recipientName(String publicName) {
        if (publicName == null) {
            return null;
        }
        int dash = publicName.indexOf(" - ");
        return (dash >= 0 ? publicName.substring(dash + 3) : publicName).trim();
    }

    private static BigDecimal decimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDateTime time(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value.trim(), SAFARICOM_TIME);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
