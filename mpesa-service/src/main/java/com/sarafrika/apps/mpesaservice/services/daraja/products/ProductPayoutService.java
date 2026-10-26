package com.sarafrika.apps.mpesaservice.services.daraja.products;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PreCheckDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.ProductPayoutDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.ProductPayoutSubmittedDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.RequestPochiPayoutRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.RequestTaxRemittanceRequest;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.ProductPayout;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.ProductPayoutRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalExecutor.Approved;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalExecutor.Outcome;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalGateway;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalSubmission;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.features.FeatureGate;
import com.sarafrika.apps.mpesaservice.services.features.ShortcodeFeatures;
import com.sarafrika.apps.mpesaservice.services.payout.SecurityCredentialService;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.services.treasury.BalanceService;
import com.sarafrika.apps.mpesaservice.services.treasury.KnownFloat;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ProductPayoutStatus;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Tax Remittance and Business to Pochi: checked and submitted to Approvals, sent once by their executors
 * (one row per approval, saved PENDING before Safaricom is called), and settled by the result under a row
 * lock, only from PENDING. No answer, or a queue timeout, leaves the outcome unknown; it is never resent. */
@Slf4j
@Service
public class ProductPayoutService {

    public static final String TAX_TYPE = "TAX_REMITTANCE";
    public static final String POCHI_TYPE = "BUSINESS_TO_POCHI";
    static final String AUDIT_SENT = "PRODUCT_PAYOUT_SENT";
    static final String UNKNOWN_OUTCOME = "Safaricom did not confirm it received this payment, so the money may "
            + "still move. Wait for the result or check the statement; do not raise it again.";
    static final String TAX_RESULT_PATH = "/tax-remittance/result";
    static final String TAX_TIMEOUT_PATH = "/tax-remittance/timeout";
    static final String POCHI_RESULT_PATH = "/pochi/result";
    static final String POCHI_TIMEOUT_PATH = "/pochi/timeout";

    private final ProductPayoutRepository payouts;
    private final MpesaShortCodeRepository shortcodes;
    private final MpesaDarajaService daraja;
    private final SecurityCredentialService credentials;
    private final DarajaProductEndpoints endpoints;
    private final FeatureGate gate;
    private final ShortcodeFeatures features;
    private final ObjectProvider<ApprovalGateway> gateway;
    private final ObjectProvider<BalanceService> balances;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate newTransaction;

    public ProductPayoutService(ProductPayoutRepository payouts, MpesaShortCodeRepository shortcodes,
                                MpesaDarajaService daraja, SecurityCredentialService credentials,
                                DarajaProductEndpoints endpoints, FeatureGate gate, ShortcodeFeatures features,
                                ObjectProvider<ApprovalGateway> gateway, ObjectProvider<BalanceService> balances,
                                AuditService audit, ObjectMapper objectMapper, PlatformTransactionManager transactions) {
        this.payouts = payouts;
        this.shortcodes = shortcodes;
        this.daraja = daraja;
        this.credentials = credentials;
        this.endpoints = endpoints;
        this.gate = gate;
        this.features = features;
        this.gateway = gateway;
        this.balances = balances;
        this.audit = audit;
        this.objectMapper = objectMapper;
        // The PENDING row must be committed before Safaricom is called, whatever the caller's transaction.
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---- submit

    @Transactional
    public ProductPayoutSubmittedDto requestTax(UUID shortcodeUuid, RequestTaxRemittanceRequest request) {
        MpesaShortCode shortcode = ready(shortcodeUuid, DarajaFeature.TAX_REMITTANCE, "Tax Remittance");
        BigDecimal amount = whole(request.amount());
        String prn = request.prn().trim();
        String remarks = StringUtils.hasText(request.remarks()) ? request.remarks().trim() : "Tax " + prn;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Tax remittance of " + kes(amount) + " to KRA (PRN " + prn + ")");
        payload.put("daraja_call", "Tax Remittance (PayTaxToKRA) from " + shortcode.getShortcode() + " to "
                + DarajaProductRequests.KRA_SHORTCODE);
        payload.put("shortcode", shortcode.getShortcode());
        payload.put("party_b", DarajaProductRequests.KRA_SHORTCODE);
        payload.put("prn", prn);
        payload.put("amount", amount.toPlainString());
        payload.put("remarks", remarks);
        payload.set("pre_checks", preChecks(shortcode, amount, true));
        return submit(TAX_TYPE, shortcode, amount, request.reason(), payload);
    }

    @Transactional
    public ProductPayoutSubmittedDto requestPochi(UUID shortcodeUuid, RequestPochiPayoutRequest request) {
        MpesaShortCode shortcode = ready(shortcodeUuid, DarajaFeature.BUSINESS_TO_POCHI, "Business to Pochi");
        BigDecimal amount = whole(request.amount());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Pochi payment of " + kes(amount) + " to " + PersonalData.maskPhone(request.phoneNumber()));
        payload.put("daraja_call", "Business to Pochi (BusinessPayToPochi) from " + shortcode.getShortcode());
        payload.put("shortcode", shortcode.getShortcode());
        payload.put("phone_number", request.phoneNumber());
        payload.put("amount", amount.toPlainString());
        payload.put("remarks", request.remarks().trim());
        payload.put("occasion", StringUtils.hasText(request.occasion()) ? request.occasion().trim() : null);
        payload.set("pre_checks", preChecks(shortcode, amount, false));
        return submit(POCHI_TYPE, shortcode, amount, request.reason(), payload);
    }

    @Transactional(readOnly = true)
    public Page<ProductPayoutDto> list(UUID shortcodeUuid, DarajaFeature product, ProductPayoutStatus status,
                                       Pageable pageable) {
        if (shortcodes.findByUuid(shortcodeUuid).isEmpty()) {
            throw new NotFoundException("Shortcode not found");
        }
        boolean visible = PersonalData.visible();
        return payouts.findForShortcode(shortcodeUuid, product, status, pageable)
                .map(p -> ProductPayoutDto.from(p, visible));
    }

    private MpesaShortCode ready(UUID shortcodeUuid, DarajaFeature feature, String label) {
        MpesaShortCode shortcode = shortcodes.findByUuid(shortcodeUuid)
                .orElseThrow(() -> new NotFoundException("Shortcode not found"));
        gate.require(shortcodeUuid, feature, label);
        if (!Boolean.TRUE.equals(shortcode.getIsActive())) {
            throw new ConflictException("Shortcode " + shortcode.getShortcode() + " is not active; payments are refused");
        }
        if (!StringUtils.hasText(shortcode.getInitiatorName())) {
            throw new ConflictException("This shortcode has no initiator; " + label + " needs one. Add it under "
                    + "Credentials.");
        }
        return shortcode;
    }

    private ProductPayoutSubmittedDto submit(String type, MpesaShortCode shortcode, BigDecimal amount, String reason,
                                             ObjectNode payload) {
        ApprovalGateway approvals = gateway.getIfAvailable();
        if (approvals == null) {
            throw UpstreamException.unavailable("Approvals are not available, so the payment was not submitted");
        }
        UUID requestUuid = approvals.submit(new ApprovalSubmission(type, shortcode.getUuid(), amount, reason.trim(),
                payload));
        return new ProductPayoutSubmittedDto(requestUuid, amount);
    }

    private ArrayNode preChecks(MpesaShortCode shortcode, BigDecimal amount, boolean tax) {
        java.util.List<PreCheckDto> checks = new java.util.ArrayList<>();
        BalanceService balanceService = balances.getIfAvailable();
        KnownFloat known = balanceService == null ? null : balanceService.knownFloat(shortcode.getUuid());
        if (known == null || !known.known()) {
            checks.add(PreCheckDto.warn("No balance is known for this shortcode yet"));
        } else if (amount.compareTo(known.utility()) > 0) {
            checks.add(PreCheckDto.warn("More than the utility balance last reported (" + kes(known.utility()) + ")"));
        } else {
            checks.add(PreCheckDto.ok("Within the utility balance last reported (" + kes(known.utility()) + ")"));
        }
        if (tax && shortcode.getEnvironment() == Environment.PRODUCTION) {
            checks.add(PreCheckDto.warn("Safaricom's portal documents only the sandbox URL of Tax Remittance; "
                    + "confirm the production endpoint with Safaricom"));
        }
        return objectMapper.valueToTree(checks);
    }

    // ---- execute (called by the executors once a checker approved)

    public Outcome execute(Approved request, DarajaFeature product) {
        Optional<ProductPayout> existing = payouts.findByApprovalRequestUuid(request.requestUuid());
        if (existing.isPresent()) {
            return already(existing.get());
        }
        MpesaShortCode shortcode = request.shortcodeUuid() == null ? null
                : shortcodes.findByUuid(request.shortcodeUuid()).orElse(null);
        if (shortcode == null) {
            return new Outcome(false, "The shortcode no longer exists");
        }
        if (!features.isEnabled(shortcode.getUuid(), product)) {
            return new Outcome(false, "The feature was switched off since the request was approved; nothing was sent");
        }
        if (!Boolean.TRUE.equals(shortcode.getIsActive())) {
            return new Outcome(false, "Shortcode " + shortcode.getShortcode() + " is not active; nothing was sent");
        }
        if (!StringUtils.hasText(shortcode.getInitiatorName())) {
            return new Outcome(false, "The shortcode has no initiator; nothing was sent");
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            return new Outcome(false, "The amount must be whole shillings above 0; nothing was sent");
        }
        ProductPayout pending = pending(request, shortcode, product);
        if (pending == null) {
            return new Outcome(false, "The approved request is missing its details; nothing was sent");
        }
        ProductPayout saved;
        try {
            saved = newTransaction.execute(status -> payouts.saveAndFlush(pending));
        } catch (DataIntegrityViolationException ex) {
            // Another execution of the same approval won the race; that one was (or is being) sent.
            return payouts.findByApprovalRequestUuid(request.requestUuid()).map(this::already).orElseThrow(() -> ex);
        }

        MpesaApiResponse<JsonNode> response = send(shortcode, saved);
        ProductPayout after = newTransaction.execute(status -> {
            ProductPayout row = payouts.lockByUuid(saved.getUuid()).orElseThrow();
            if (row.getStatus() != ProductPayoutStatus.PENDING) {
                return row; // The result beat this reply and already settled it.
            }
            JsonNode data = response.data();
            if (response.success() && "0".equals(DarajaReplies.text(data, "ResponseCode"))) {
                row.setConversationId(DarajaReplies.text(data, "ConversationID"));
                if (row.getOriginatorConversationId() == null) {
                    row.setOriginatorConversationId(DarajaReplies.text(data, "OriginatorConversationID"));
                }
                row.setResultDesc(DarajaReplies.truncate(DarajaReplies.text(data, "ResponseDescription"), 500));
            } else if (DarajaReplies.outcomeUnknown(response)) {
                row.setOutcomeUnknown(true);
                row.setResultDesc(UNKNOWN_OUTCOME);
            } else {
                row.setStatus(ProductPayoutStatus.FAILED);
                row.setResultDesc(DarajaReplies.truncate(DarajaReplies.refusal(response, "ResponseDescription"), 500));
                row.setCompletedAt(LocalDateTime.now());
            }
            return payouts.save(row);
        });
        audit.record(AUDIT_SENT, "PRODUCT_PAYOUT", after.getUuid().toString(), null, Map.of(
                "product", product.name(), "approval_request_uuid", request.requestUuid().toString(),
                "status", after.getStatus().name(), "outcome_unknown", after.isOutcomeUnknown(),
                "amount", after.getAmount().toPlainString()));
        if (after.getStatus() == ProductPayoutStatus.FAILED) {
            return new Outcome(false, "Safaricom refused the payment: " + after.getResultDesc());
        }
        return new Outcome(true, after.isOutcomeUnknown() ? UNKNOWN_OUTCOME
                : "Sent to Safaricom; the payment settles when the result arrives");
    }

    private MpesaApiResponse<JsonNode> send(MpesaShortCode shortcode, ProductPayout payout) {
        Map<String, Object> body;
        try {
            String credential = credentials.credentialFor(shortcode);
            body = payout.getProduct() == DarajaFeature.TAX_REMITTANCE
                    ? DarajaProductRequests.taxRemittance(shortcode.getInitiatorName(), credential,
                    shortcode.getShortcode(), payout.getAmount(), payout.getAccountReference(), payout.getRemarks(),
                    endpoints.callbackUrl(TAX_TIMEOUT_PATH), endpoints.callbackUrl(TAX_RESULT_PATH))
                    : DarajaProductRequests.businessToPochi(payout.getOriginatorConversationId(),
                    shortcode.getInitiatorName(), credential, shortcode.getShortcode(), payout.getPartyB(),
                    payout.getAmount(), payout.getRemarks(), payout.getOccasion(),
                    endpoints.callbackUrl(POCHI_TIMEOUT_PATH), endpoints.callbackUrl(POCHI_RESULT_PATH));
        } catch (RuntimeException e) {
            // Nothing reached Safaricom, so this refusal is final.
            log.error("{} {} not sent: {}", payout.getProduct(), payout.getUuid(), e.toString());
            return MpesaApiResponse.error(MpesaErrorResponse.NOT_SENT, "The payment could not be prepared: "
                    + e.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY.value());
        }
        var url = endpoints.requestUrl(payout.getProduct(), shortcode.getEnvironment());
        return daraja.callProduct(shortcode, payout.getProduct().name(),
                (client, bearer) -> client.postProduct(url, bearer, body));
    }

    private static ProductPayout pending(Approved request, MpesaShortCode shortcode, DarajaFeature product) {
        JsonNode payload = request.payload();
        ProductPayout payout = new ProductPayout();
        payout.setShortcodeUuid(shortcode.getUuid());
        payout.setProduct(product);
        payout.setApprovalRequestUuid(request.requestUuid());
        payout.setAmount(request.amount());
        payout.setRemarks(DarajaReplies.text(payload, "remarks"));
        if (product == DarajaFeature.TAX_REMITTANCE) {
            payout.setCommandId(DarajaProductRequests.PAY_TAX_TO_KRA);
            payout.setPartyB(DarajaProductRequests.KRA_SHORTCODE);
            payout.setAccountReference(DarajaReplies.text(payload, "prn"));
            if (payout.getAccountReference() == null) {
                return null;
            }
        } else {
            payout.setCommandId(DarajaProductRequests.BUSINESS_PAY_TO_POCHI);
            payout.setPartyB(DarajaReplies.text(payload, "phone_number"));
            payout.setOccasion(DarajaReplies.text(payload, "occasion"));
            // Ours, so a lost reply still matches its result and Safaricom refuses a duplicate.
            payout.setOriginatorConversationId("sar-pochi-" + UUID.randomUUID());
            if (payout.getPartyB() == null) {
                return null;
            }
        }
        if (payout.getRemarks() == null) {
            return null;
        }
        payout.setMaker(request.maker());
        payout.setChecker(request.checker());
        payout.setCreatedBy(request.checker());
        return payout;
    }

    private Outcome already(ProductPayout payout) {
        return new Outcome(payout.getStatus() != ProductPayoutStatus.FAILED,
                "Already sent as payment " + payout.getUuid() + " (" + payout.getStatus() + ")");
    }

    // ---- settle

    /** The result callback: ResultCode 0 is success, anything else (2001, SFC_IC0003...) a failure. The row is
     * locked and only a PENDING payment takes a result, so repeats change nothing. */
    @Transactional
    public Optional<ProductPayout> applyResult(DarajaFeature product, JsonNode body, String raw) {
        JsonNode result = body == null ? null : body.path("Result");
        if (result == null || !result.isObject()) {
            throw new IllegalArgumentException("The callback has no Result object");
        }
        Optional<ProductPayout> match = lock(product, DarajaReplies.text(result, "OriginatorConversationID"),
                DarajaReplies.text(result, "ConversationID"));
        if (match.isEmpty()) {
            log.warn("{} result for an unknown payment {}", product, DarajaReplies.text(result, "ConversationID"));
            return Optional.empty();
        }
        ProductPayout payout = match.get();
        if (payout.getStatus() != ProductPayoutStatus.PENDING) {
            log.info("Ignoring repeat {} result for {} already {}", product, payout.getUuid(), payout.getStatus());
            return match;
        }
        String code = DarajaReplies.text(result, "ResultCode");
        boolean success = "0".equals(code);
        payout.setStatus(success ? ProductPayoutStatus.SUCCESS : ProductPayoutStatus.FAILED);
        payout.setOutcomeUnknown(false);
        payout.setResultCode(DarajaReplies.truncate(code, 20));
        payout.setResultDesc(DarajaReplies.truncate(DarajaReplies.text(result, "ResultDesc"), 500));
        if (success) {
            payout.setTransactionId(DarajaReplies.text(result, "TransactionID"));
            payout.setReceiverName(DarajaReplies.truncate(parameter(result, "ReceiverPartyPublicName"), 200));
        }
        if (payout.getConversationId() == null) {
            payout.setConversationId(DarajaReplies.text(result, "ConversationID"));
        }
        payout.setRawResult(raw);
        payout.setCompletedAt(LocalDateTime.now());
        return Optional.of(payouts.save(payout));
    }

    /** Safaricom could not process the request in time. The outcome is unknown, so it stays PENDING. */
    @Transactional
    public Optional<ProductPayout> applyTimeout(DarajaFeature product, JsonNode body) {
        JsonNode result = body != null && body.has("Result") ? body.get("Result") : body;
        String description = DarajaReplies.text(result, "ResultDesc");
        return lock(product, DarajaReplies.text(result, "OriginatorConversationID"),
                DarajaReplies.text(result, "ConversationID")).map(payout -> {
            if (payout.getStatus() == ProductPayoutStatus.PENDING && payout.getTimedOutAt() == null) {
                payout.setTimedOutAt(LocalDateTime.now());
                payout.setOutcomeUnknown(true);
                payout.setResultDesc(DarajaReplies.truncate(description != null ? description : UNKNOWN_OUTCOME, 500));
                return payouts.save(payout);
            }
            return payout;
        });
    }

    private Optional<ProductPayout> lock(DarajaFeature product, String originatorConversationId,
                                         String conversationId) {
        Optional<ProductPayout> byOriginator = StringUtils.hasText(originatorConversationId)
                ? payouts.lockByOriginatorConversationId(product, originatorConversationId) : Optional.empty();
        return byOriginator.isPresent() || !StringUtils.hasText(conversationId) ? byOriginator
                : payouts.lockByConversationId(product, conversationId);
    }

    // Result.ResultParameters.ResultParameter is a list of {Key, Value}, or a single one.
    private static String parameter(JsonNode result, String key) {
        JsonNode items = result.path("ResultParameters").path("ResultParameter");
        for (JsonNode item : items.isArray() ? items : java.util.List.of(items)) {
            if (key.equals(DarajaReplies.text(item, "Key"))) {
                return DarajaReplies.text(item, "Value");
            }
        }
        return null;
    }

    private static BigDecimal whole(BigDecimal amount) {
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidRequestException("amount", "must be whole shillings");
        }
        return amount.setScale(0, java.math.RoundingMode.UNNECESSARY);
    }

    private static String kes(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ENGLISH);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "KES " + format.format(amount);
    }
}
