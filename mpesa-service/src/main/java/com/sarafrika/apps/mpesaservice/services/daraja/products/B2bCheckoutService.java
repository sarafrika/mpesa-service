package com.sarafrika.apps.mpesaservice.services.daraja.products;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.B2bCheckoutDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.StartB2bCheckoutRequest;
import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.B2bCheckoutRequest;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.B2bCheckoutRequestRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackOutcome;
import com.sarafrika.apps.mpesaservice.services.features.FeatureGate;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bCheckoutStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** B2B Express Checkout (developer.safaricom.co.ke/apis/B2BExpressCheckout): a USSD push asking a merchant's
 * till to pay one of our paybills. The callback, matched on requestId, settles it once; a success becomes an
 * incoming payment (one per M-Pesa receipt) and publishes b2b_checkout.completed. */
@Slf4j
@Service
public class B2bCheckoutService {

    private static final java.time.ZoneId NAIROBI = java.time.ZoneId.of("Africa/Nairobi");

    public static final String EVENT_COMPLETED = PaymentEvent.B2B_CHECKOUT_COMPLETED;
    static final String AUDIT_STARTED = "B2B_EXPRESS_CHECKOUT_STARTED";
    static final String RESULT_PATH = "/b2b-checkout/result";
    static final String CANCELLED_CODE = "4001";
    static final String NO_MATCH = "No B2B Express Checkout push here has this requestId, so nothing was recorded. "
            + "Check the requestId, and replay it if its push turns up.";
    static final String UNKNOWN_OUTCOME = "Safaricom did not confirm the push was sent, so the merchant may still "
            + "be prompted. Wait for the callback; do not push again.";

    private final B2bCheckoutRequestRepository checkouts;
    private final MpesaShortCodeRepository shortcodes;
    private final MpesaIncomingPaymentRepository incoming;
    private final MpesaDarajaService daraja;
    private final DarajaProductEndpoints endpoints;
    private final FeatureGate gate;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate newTransaction;

    public B2bCheckoutService(B2bCheckoutRequestRepository checkouts, MpesaShortCodeRepository shortcodes,
                              MpesaIncomingPaymentRepository incoming, MpesaDarajaService daraja,
                              DarajaProductEndpoints endpoints, FeatureGate gate, AuditService audit,
                              ApplicationEventPublisher events, ObjectMapper objectMapper,
                              PlatformTransactionManager transactions) {
        this.checkouts = checkouts;
        this.shortcodes = shortcodes;
        this.incoming = incoming;
        this.daraja = daraja;
        this.endpoints = endpoints;
        this.gate = gate;
        this.audit = audit;
        this.events = events;
        this.objectMapper = objectMapper;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Saves the push PENDING, then asks Safaricom; a refusal is a 502 (the row stays FAILED), no answer leaves
     * it PENDING with the outcome unknown. */
    public B2bCheckoutDto start(UUID shortcodeUuid, StartB2bCheckoutRequest request) {
        MpesaShortCode shortcode = shortcodes.findByUuid(shortcodeUuid)
                .orElseThrow(() -> new NotFoundException("Shortcode not found"));
        gate.require(shortcodeUuid, DarajaFeature.B2B_EXPRESS_CHECKOUT, "B2B Express Checkout");
        if (!Boolean.TRUE.equals(shortcode.getIsActive())) {
            throw new ConflictException("Shortcode " + shortcode.getShortcode() + " is not active; pushes are refused");
        }
        if (shortcode.getShortcodeType() != ShortcodeType.PAYBILL) {
            throw new ConflictException("B2B Express Checkout pays into a paybill; this shortcode is a "
                    + shortcode.getShortcodeType());
        }
        BigDecimal amount = request.amount();
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidRequestException("amount", "must be whole shillings");
        }
        B2bCheckoutRequest row = new B2bCheckoutRequest();
        row.setShortcodeUuid(shortcodeUuid);
        row.setRequestRefId(UUID.randomUUID());
        row.setPrimaryShortCode(request.payerTill().trim());
        row.setReceiverShortCode(shortcode.getShortcode());
        row.setAmount(amount);
        row.setPaymentRef(request.paymentRef().trim());
        row.setPartnerName(StringUtils.hasText(request.partnerName()) ? request.partnerName().trim()
                : DarajaReplies.truncate(shortcode.getBusinessName(), 100));
        row.setCreatedBy(CurrentActor.name());
        B2bCheckoutRequest saved = newTransaction.execute(status -> checkouts.saveAndFlush(row));
        audit.record(AUDIT_STARTED, "B2B_CHECKOUT", saved.getUuid().toString(), null, Map.of(
                "payer_till", saved.getPrimaryShortCode(), "amount", amount.toPlainString(),
                "payment_ref", saved.getPaymentRef(), "request_ref_id", saved.getRequestRefId().toString()));

        Map<String, Object> body = DarajaProductRequests.b2bExpressCheckout(saved.getPrimaryShortCode(),
                saved.getReceiverShortCode(), amount, saved.getPaymentRef(), endpoints.callbackUrl(shortcode, RESULT_PATH),
                saved.getPartnerName(), saved.getRequestRefId().toString());
        var url = endpoints.requestUrl(DarajaFeature.B2B_EXPRESS_CHECKOUT, shortcode.getEnvironment());
        MpesaApiResponse<JsonNode> response = daraja.callProduct(shortcode, "B2B Express Checkout",
                (client, bearer) -> client.postProduct(url, bearer, body));
        B2bCheckoutRequest after = newTransaction.execute(status -> acknowledge(saved.getUuid(), response));
        if (after.getStatus() == B2bCheckoutStatus.FAILED && after.getRawResult() == null) {
            throw UpstreamException.badGateway("Safaricom refused the push: " + after.getResultDesc());
        }
        return B2bCheckoutDto.from(after);
    }

    // The documented acknowledgement is {"code":"0","status":"USSD Initiated Successfully"}.
    private B2bCheckoutRequest acknowledge(UUID uuid, MpesaApiResponse<JsonNode> response) {
        B2bCheckoutRequest row = checkouts.lockByUuid(uuid).orElseThrow();
        if (row.getStatus() != B2bCheckoutStatus.PENDING) {
            return row; // The callback beat this reply.
        }
        if (response.success() && "0".equals(DarajaReplies.text(response.data(), "code"))) {
            row.setResultDesc(DarajaReplies.truncate(DarajaReplies.text(response.data(), "status"), 500));
        } else if (DarajaReplies.outcomeUnknown(response)) {
            row.setOutcomeUnknown(true);
            row.setResultDesc(UNKNOWN_OUTCOME);
        } else {
            row.setStatus(B2bCheckoutStatus.FAILED);
            row.setResultCode(DarajaReplies.truncate(DarajaReplies.text(response.data(), "code"), 20));
            row.setResultDesc(DarajaReplies.truncate(DarajaReplies.refusal(response, "status"), 500));
            row.setCompletedAt(LocalDateTime.now());
        }
        return checkouts.save(row);
    }

    @Transactional(readOnly = true)
    public Page<B2bCheckoutDto> list(UUID shortcodeUuid, B2bCheckoutStatus status, Pageable pageable) {
        if (shortcodes.findByUuid(shortcodeUuid).isEmpty()) {
            throw new NotFoundException("Shortcode not found");
        }
        return checkouts.findForShortcode(shortcodeUuid, status, pageable).map(B2bCheckoutDto::from);
    }

    /** resultCode 0 is a success, 4001 the merchant cancelling, anything else (4102, 4104, 4201...) a failure.
     * The row is locked and only a PENDING push takes a callback, so repeats change nothing. */
    @Transactional
    public Optional<B2bCheckoutRequest> applyResult(JsonNode body, String raw) {
        UUID requestId = uuid(DarajaReplies.text(body, "requestId"));
        Optional<B2bCheckoutRequest> match = requestId == null ? Optional.empty()
                : checkouts.lockByRequestRefId(requestId);
        if (match.isEmpty()) {
            log.warn("B2B Express Checkout callback for an unknown request {}", DarajaReplies.text(body, "requestId"));
            CallbackOutcome.failed(NO_MATCH);
            return Optional.empty();
        }
        B2bCheckoutRequest checkout = match.get();
        if (checkout.getStatus() != B2bCheckoutStatus.PENDING) {
            log.info("Ignoring repeat checkout callback for {} already {}", checkout.getUuid(), checkout.getStatus());
            return match;
        }
        String code = DarajaReplies.text(body, "resultCode");
        checkout.setResultCode(DarajaReplies.truncate(code, 20));
        checkout.setResultDesc(DarajaReplies.truncate(DarajaReplies.text(body, "resultDesc"), 500));
        checkout.setOutcomeUnknown(false);
        checkout.setRawResult(raw);
        checkout.setCompletedAt(LocalDateTime.now());
        if ("0".equals(code)) {
            checkout.setStatus(B2bCheckoutStatus.SUCCESS);
            checkout.setTransactionId(DarajaReplies.text(body, "transactionId"));
            checkout.setConversationId(DarajaReplies.text(body, "conversationID"));
            if (StringUtils.hasText(checkout.getTransactionId())) {
                checkout.setIncomingPaymentUuid(recordPayment(checkout, body).getUuid());
            }
        } else {
            checkout.setStatus(CANCELLED_CODE.equals(code) ? B2bCheckoutStatus.CANCELLED : B2bCheckoutStatus.FAILED);
        }
        return Optional.of(checkouts.save(checkout));
    }

    // One incoming payment per receipt: a receipt already stored (e.g. a replayed callback) is linked, not copied.
    private MpesaIncomingPayment recordPayment(B2bCheckoutRequest checkout, JsonNode body) {
        Optional<MpesaIncomingPayment> existing = incoming.findByTransactionId(checkout.getTransactionId());
        if (existing.isPresent()) {
            return existing.get();
        }
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setShortcodeUuid(checkout.getShortcodeUuid());
        payment.setPaymentType(IncomingPaymentType.B2B_EXPRESS_CHECKOUT);
        payment.setTransactionId(checkout.getTransactionId());
        payment.setAmount(amount(body, checkout.getAmount()));
        payment.setAccountReference(checkout.getPaymentRef());
        payment.setTransactionDesc("B2B Express Checkout from till " + checkout.getPrimaryShortCode());
        payment.setBusinessShortCode(checkout.getReceiverShortCode());
        payment.setResultCode(0);
        payment.setResultDesc(checkout.getResultDesc());
        payment.setStatus(IncomingPaymentStatus.SUCCESS);
        // Transaction times are East Africa Time, as Daraja's own timestamps are.
        payment.setTransactionDate(LocalDateTime.now(NAIROBI));
        payment.setProcessedAt(LocalDateTime.now());
        payment.setRawCallbackData(objectMapper.convertValue(body, new TypeReference<Map<String, Object>>() {}));
        MpesaIncomingPayment saved = incoming.saveAndFlush(payment);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("transaction_id", saved.getTransactionId());
        data.put("amount", saved.getAmount());
        data.put("account_reference", saved.getAccountReference());
        data.put("payer_till", checkout.getPrimaryShortCode());
        data.put("request_ref_id", checkout.getRequestRefId().toString());
        events.publishEvent(new PaymentEvent(EVENT_COMPLETED, saved.getShortcodeUuid(), saved.getUuid(), null, data));
        return saved;
    }

    private static BigDecimal amount(JsonNode body, BigDecimal fallback) {
        try {
            String value = DarajaReplies.text(body, "amount");
            return value == null ? fallback : new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
