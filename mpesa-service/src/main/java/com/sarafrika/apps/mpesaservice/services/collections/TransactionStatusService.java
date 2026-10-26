package com.sarafrika.apps.mpesaservice.services.collections;

import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkStatusResponse;
import com.sarafrika.apps.mpesaservice.dtos.TransactionStatusResponse;
import com.sarafrika.apps.mpesaservice.dtos.TransactionStatusResultData;
import com.sarafrika.apps.mpesaservice.dtos.TransactionStatusResultParameters;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.StatusQueryDto;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.TransactionStatusQuery;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaOutgoingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.TransactionStatusQueryRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.StatusQueryMethod;
import com.sarafrika.apps.mpesaservice.utils.enums.StatusQueryStatus;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Asks Safaricom where a payment stands (STK query or Transaction Status) and settles it from the answer. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionStatusService {

    static final String AUDIT_ACTION = "TRANSACTION_STATUS_QUERIED";
    private static final Set<String> CLIENT_SIDE = Set.of("CLIENT_ERROR", "AUTH_FAILED", "INVALID_ARGUMENT",
            "SERVER_ERROR", "INVALID_CONFIG", "API_ERROR");

    private final MpesaIncomingPaymentRepository incomingRepository;
    private final MpesaOutgoingPaymentRepository outgoingRepository;
    private final MpesaShortCodeRepository shortCodeRepository;
    private final TransactionStatusQueryRepository queryRepository;
    private final MpesaDarajaService darajaService;
    private final MpesaIncomingPaymentService incomingPaymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    /** From the console: an STK request without a receipt gets an STK query, anything else Transaction Status. */
    @Transactional
    public StatusQueryDto request(UUID paymentUuid) {
        TransactionStatusQuery query;
        Optional<MpesaIncomingPayment> incoming = incomingRepository.findByUuid(paymentUuid);
        if (incoming.isPresent()) {
            MpesaIncomingPayment payment = incoming.get();
            MpesaShortCode shortcode = shortcodeOf(payment.getShortcodeUuid());
            String receipt = receipt(payment);
            if (payment.getPaymentType() == IncomingPaymentType.STK_PUSH && receipt == null) {
                query = stkQuery(payment, shortcode, true).orElseThrow();
            } else if (receipt == null) {
                throw new ConflictException("This payment has no M-Pesa receipt to ask Safaricom about");
            } else {
                query = transactionStatus(paymentUuid, TransactionStatusQuery.IN, shortcode, receipt, null);
            }
        } else {
            MpesaOutgoingPayment payout = outgoingRepository.findByUuid(paymentUuid)
                    .orElseThrow(() -> new NotFoundException("Transaction not found"));
            MpesaShortCode shortcode = shortcodeOf(payout.getShortcodeUuid());
            String receipt = StringUtils.hasText(payout.getTransactionId()) ? payout.getTransactionId() : null;
            query = transactionStatus(paymentUuid, TransactionStatusQuery.OUT, shortcode, receipt,
                    receipt == null ? payout.getOriginatorConversationId() : null);
        }
        auditService.record(AUDIT_ACTION, "TRANSACTION", paymentUuid.toString(), null,
                Map.of("method", query.getMethod().name(), "query_uuid", query.getUuid().toString()));
        return StatusQueryDto.from(query);
    }

    /** STK push query; settles a pending payment when Safaricom has a final answer. Empty when it has none yet
     * (only when failOnError is false; otherwise Daraja failures throw {@link UpstreamException}). */
    @Transactional
    public Optional<TransactionStatusQuery> stkQuery(MpesaIncomingPayment payment, MpesaShortCode shortcode,
                                                     boolean failOnError) {
        MpesaApiResponse<StkStatusResponse> response =
                darajaService.queryStkPushStatus(shortcode.getUuid(), payment.getCheckoutRequestId());
        if (!response.success() || response.data() == null || response.data().resultCode() == null) {
            if (failOnError) {
                throw upstream(response, "Safaricom has no final answer for this STK request yet");
            }
            return Optional.empty();
        }
        StkStatusResponse data = response.data();
        TransactionStatusQuery query = newQuery(payment.getUuid(), TransactionStatusQuery.IN, shortcode,
                StatusQueryMethod.STK_QUERY);
        query.setStatus(StatusQueryStatus.COMPLETED);
        query.setResultCode(data.resultCode());
        query.setResultDesc(data.resultDesc());
        query.setReceipt(data.mpesaReceiptNumber());
        query.setCompletedAt(LocalDateTime.now());
        TransactionStatusQuery saved = queryRepository.save(query);
        if (payment.getStatus() == IncomingPaymentStatus.PENDING) {
            Map<String, Object> callback = new LinkedHashMap<>();
            callback.put("Source", "STK push query");
            callback.put("MerchantRequestID", data.merchantRequestId());
            callback.put("CheckoutRequestID", payment.getCheckoutRequestId());
            callback.put("ResultCode", data.resultCode());
            callback.put("ResultDesc", data.resultDesc());
            if (data.mpesaReceiptNumber() != null) {
                callback.put("CallbackMetadata", Map.of("Item", List.of(
                        Map.of("Name", "MpesaReceiptNumber", "Value", data.mpesaReceiptNumber()))));
            }
            incomingPaymentService.processStkPushCallback(payment.getCheckoutRequestId(), callback);
        }
        return Optional.of(saved);
    }

    /** Safaricom's Transaction Status result; settles the payment if it was still pending. */
    @Transactional
    public Optional<TransactionStatusQuery> applyResult(TransactionStatusResultData result, String rawJson) {
        Optional<TransactionStatusQuery> found = find(result.conversationId(), result.originatorConversationId());
        if (found.isEmpty()) {
            log.warn("Transaction status result for unknown conversation {} / {}", result.conversationId(),
                    result.originatorConversationId());
            return Optional.empty();
        }
        TransactionStatusQuery query = found.get();
        if (query.getStatus() != StatusQueryStatus.PENDING && query.getStatus() != StatusQueryStatus.TIMED_OUT) {
            return Optional.of(query);
        }
        TransactionStatusResultParameters params = result.resultParameters();
        query.setResultCode(result.resultCode());
        query.setResultDesc(result.resultDesc());
        query.setRawResult(rawJson);
        query.setCompletedAt(LocalDateTime.now());
        query.setStatus(result.isSuccessful() ? StatusQueryStatus.COMPLETED : StatusQueryStatus.FAILED);
        if (params != null && params.resultParameters() != null) {
            query.setTransactionStatus(params.getTransactionStatus());
            query.setReceipt(params.getReceiptNumber());
        }
        TransactionStatusQuery saved = queryRepository.save(query);
        if (result.isSuccessful() && saved.getTransactionStatus() != null) {
            settle(saved);
        }
        return Optional.of(saved);
    }

    @Transactional
    public Optional<TransactionStatusQuery> applyTimeout(String conversationId, String originatorConversationId) {
        return find(conversationId, originatorConversationId).map(query -> {
            if (query.getStatus() == StatusQueryStatus.PENDING) {
                query.setStatus(StatusQueryStatus.TIMED_OUT);
                query.setCompletedAt(LocalDateTime.now());
            }
            return queryRepository.save(query);
        });
    }

    private void settle(TransactionStatusQuery query) {
        String outcome = query.getTransactionStatus().trim().toLowerCase(Locale.ROOT);
        boolean completed = outcome.equals("completed");
        boolean cancelled = outcome.startsWith("cancel");
        if (!completed && !outcome.equals("failed") && !cancelled && !outcome.equals("expired")) {
            return;
        }
        if (TransactionStatusQuery.IN.equals(query.getDirection())) {
            incomingRepository.findByUuid(query.getPaymentUuid())
                    .filter(p -> p.getStatus() == IncomingPaymentStatus.PENDING && p.getCheckoutRequestId() != null)
                    .ifPresent(p -> {
                        Map<String, Object> callback = new LinkedHashMap<>();
                        callback.put("Source", "Transaction Status");
                        callback.put("CheckoutRequestID", p.getCheckoutRequestId());
                        callback.put("ResultCode", completed ? 0 : cancelled ? 1032 : 1);
                        callback.put("ResultDesc", "Transaction Status: " + query.getTransactionStatus());
                        if (completed && query.getReceipt() != null) {
                            callback.put("CallbackMetadata", Map.of("Item", List.of(
                                    Map.of("Name", "MpesaReceiptNumber", "Value", query.getReceipt()))));
                        }
                        incomingPaymentService.processStkPushCallback(p.getCheckoutRequestId(), callback);
                    });
            return;
        }
        outgoingRepository.findByUuid(query.getPaymentUuid())
                .filter(o -> o.getStatus() == OutgoingPaymentStatus.PENDING)
                .ifPresent(o -> {
                    o.setStatus(completed ? OutgoingPaymentStatus.SUCCESS
                            : cancelled ? OutgoingPaymentStatus.CANCELLED : OutgoingPaymentStatus.FAILED);
                    if (completed && query.getReceipt() != null) {
                        o.setTransactionId(query.getReceipt());
                    }
                    o.setResultDesc("Transaction Status: " + query.getTransactionStatus());
                    o.setProcessedAt(LocalDateTime.now());
                    MpesaOutgoingPayment saved = outgoingRepository.save(o);
                    if (saved.getPaymentType() == OutgoingPaymentType.B2C) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("receipt", saved.getTransactionId());
                        data.put("client_reference", saved.getClientReference());
                        data.put("amount", saved.getAmount());
                        data.put("status", saved.getStatus().name());
                        data.put("source", "transaction_status");
                        eventPublisher.publishEvent(new PaymentEvent(
                                completed ? PaymentEvent.B2C_COMPLETED : PaymentEvent.B2C_FAILED,
                                saved.getShortcodeUuid(), saved.getUuid(), saved.getClientId(), data));
                    }
                });
    }

    private TransactionStatusQuery transactionStatus(UUID paymentUuid, String direction, MpesaShortCode shortcode,
                                                     String receipt, String originalConversationId) {
        if (receipt == null && originalConversationId == null) {
            throw new ConflictException("This payment has no receipt or conversation id to ask Safaricom about");
        }
        if (!StringUtils.hasText(shortcode.getInitiatorName())) {
            throw new ConflictException("Shortcode " + shortcode.getShortcode()
                    + " has no initiator set, which Safaricom's Transaction Status needs");
        }
        MpesaApiResponse<TransactionStatusResponse> response = darajaService.queryTransactionStatus(shortcode,
                receipt, originalConversationId, "Console status query");
        if (!response.success() || response.data() == null) {
            throw upstream(response, "Safaricom refused the status query");
        }
        TransactionStatusQuery query = newQuery(paymentUuid, direction, shortcode, StatusQueryMethod.TRANSACTION_STATUS);
        query.setQueriedTransactionId(receipt != null ? receipt : originalConversationId);
        query.setConversationId(response.data().conversationId());
        query.setOriginatorConversationId(response.data().originatorConversationId());
        return queryRepository.save(query);
    }

    private TransactionStatusQuery newQuery(UUID paymentUuid, String direction, MpesaShortCode shortcode,
                                            StatusQueryMethod method) {
        TransactionStatusQuery query = new TransactionStatusQuery();
        query.setPaymentUuid(paymentUuid);
        query.setDirection(direction);
        query.setShortcodeUuid(shortcode.getUuid());
        query.setMethod(method);
        query.setCreatedBy(CurrentActor.name());
        return query;
    }

    private Optional<TransactionStatusQuery> find(String conversationId, String originatorConversationId) {
        Optional<TransactionStatusQuery> byConversation = StringUtils.hasText(conversationId)
                ? queryRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversationId) : Optional.empty();
        if (byConversation.isPresent() || !StringUtils.hasText(originatorConversationId)) {
            return byConversation;
        }
        return queryRepository.findFirstByOriginatorConversationIdOrderByCreatedAtDesc(originatorConversationId);
    }

    private MpesaShortCode shortcodeOf(UUID shortcodeUuid) {
        if (shortcodeUuid == null) {
            throw new ConflictException("This payment reached a shortcode that is not configured here");
        }
        return shortCodeRepository.findByUuid(shortcodeUuid)
                .orElseThrow(() -> new ConflictException("This payment's shortcode no longer exists"));
    }

    /** The M-Pesa receipt, or null while an STK request still carries its checkout id as a placeholder. */
    static String receipt(MpesaIncomingPayment payment) {
        String id = payment.getTransactionId();
        return !StringUtils.hasText(id) || Objects.equals(id, payment.getCheckoutRequestId()) ? null : id;
    }

    // Credentials, a bad request or Safaricom's own 5xx are a 502; not reaching Safaricom at all is a 503.
    static UpstreamException upstream(MpesaApiResponse<?> response, String message) {
        String code = response.error() == null ? null : response.error().errorCode();
        String detail = response.error() == null ? null : response.error().errorMessage();
        if (code != null && CLIENT_SIDE.contains(code)) {
            return UpstreamException.badGateway(detail == null ? message : message + " (" + detail + ")");
        }
        if (response.success()) {
            return UpstreamException.badGateway(message);
        }
        return UpstreamException.unavailable("Safaricom could not be reached; try again shortly");
    }
}
