package com.sarafrika.apps.mpesaservice.services.treasury;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarafrika.apps.mpesaservice.dtos.B2BResultParameters;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackOutcome;
import com.sarafrika.apps.mpesaservice.dtos.B2BPaymentRequest;
import com.sarafrika.apps.mpesaservice.dtos.B2BResult;
import com.sarafrika.apps.mpesaservice.dtos.B2BTransferResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.models.B2bDestination;
import com.sarafrika.apps.mpesaservice.models.B2bTransfer;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.B2bDestinationRepository;
import com.sarafrika.apps.mpesaservice.repositories.B2bTransferRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalExecutor.Approved;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalExecutor.Outcome;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.daraja.NotSent;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Sends an approved sweep once (one row per approval request, saved PENDING before Safaricom is called) and
 * settles it from the B2B result. No answer, or a queue timeout, leaves it PENDING with the outcome unknown:
 * the money may still move, so it is never resent automatically. */
@Slf4j
@Service
public class B2bTransferService {

    static final String AUDIT_SENT = "B2B_SWEEP_SENT";
    static final String AUDIT_CLOSED = "B2B_TRANSFER_CLOSED";
    static final String NO_MATCH = "No B2B transfer matches this result. Replay it later, or attach it to its "
            + "transfer under Inbox after checking the statement.";
    // How far back a result may match a transfer without ids; a setting of this service, not Safaricom's.
    static final Duration FALLBACK_WINDOW = Duration.ofDays(3);

    static final String UNKNOWN_OUTCOME = "Safaricom did not confirm it received this sweep, so the money may "
            + "still move. Wait for the result or check the statement; do not raise the sweep again.";

    private final B2bTransferRepository transfers;
    private final B2bDestinationRepository destinations;
    private final MpesaShortCodeRepository shortcodes;
    private final MpesaDarajaService daraja;
    private final AuditService audit;
    private final TransactionTemplate newTransaction;

    public B2bTransferService(B2bTransferRepository transfers, B2bDestinationRepository destinations,
                              MpesaShortCodeRepository shortcodes, MpesaDarajaService daraja, AuditService audit,
                              PlatformTransactionManager transactions) {
        this.transfers = transfers;
        this.destinations = destinations;
        this.shortcodes = shortcodes;
        this.daraja = daraja;
        this.audit = audit;
        // Our own transactions: the PENDING row must be committed before Safaricom is called, whatever the caller.
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Outcome execute(Approved request) {
        Optional<B2bTransfer> existing = transfers.findByApprovalRequestUuid(request.requestUuid());
        if (existing.isPresent()) {
            return already(existing.get());
        }
        JsonNode payload = request.payload();
        MpesaShortCode shortcode = request.shortcodeUuid() == null ? null
                : shortcodes.findByUuid(request.shortcodeUuid()).orElse(null);
        if (shortcode == null) {
            return new Outcome(false, "The shortcode no longer exists");
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
        UUID destinationUuid = uuid(text(payload, "destination_uuid"));
        Optional<B2bDestination> found = destinationUuid == null ? Optional.empty()
                : destinations.findByUuidAndShortcodeUuid(destinationUuid, shortcode.getUuid());
        if (found.isEmpty() || !found.get().isActive()) {
            return new Outcome(false, "The destination was removed or deactivated; nothing was sent");
        }
        B2bDestination destination = found.get();
        if (changed(destination, payload)) {
            return new Outcome(false, "The destination changed after the sweep was requested; nothing was sent. "
                    + "Raise a new sweep.");
        }

        B2bTransfer saved;
        try {
            saved = newTransaction.execute(status -> transfers.saveAndFlush(pending(request, destination, payload)));
        } catch (DataIntegrityViolationException ex) {
            // Another execution of the same approval won the race; that one was (or is being) sent.
            return transfers.findByApprovalRequestUuid(request.requestUuid()).map(this::already).orElseThrow(() -> ex);
        }

        MpesaApiResponse<B2BTransferResponse> response = daraja.sendB2BPayment(shortcode, new B2BPaymentRequest(
                saved.getCommandId(), saved.getReceiverShortcode(), saved.getAmount(), saved.getAccountReference(),
                saved.getRemarks()));
        B2bTransfer after = newTransaction.execute(status -> {
            B2bTransfer row = transfers.lockByUuid(saved.getUuid()).orElseThrow();
            if (row.getStatus() != B2bTransferStatus.PENDING) {
                return row; // The result beat this reply and already settled it.
            }
            if (response.success() && response.data() != null && response.data().isAccepted()) {
                row.setConversationId(response.data().conversationId());
                row.setOriginatorConversationId(response.data().originatorConversationId());
                row.setResultDesc(response.data().responseDescription());
            } else if (outcomeUnknown(response)) {
                row.setOutcomeUnknown(true);
                row.setResultDesc(UNKNOWN_OUTCOME);
            } else if (NotSent.is(response)) {
                row.setStatus(B2bTransferStatus.FAILED);
                row.setResultDesc(truncate(NotSent.describe(response, "sweep")));
                row.setCompletedAt(LocalDateTime.now());
            } else {
                row.setStatus(B2bTransferStatus.FAILED);
                row.setResultDesc(truncate(response.error() != null ? response.error().errorMessage()
                        : response.data() != null ? response.data().responseDescription()
                        : "Safaricom refused the request"));
                row.setCompletedAt(LocalDateTime.now());
            }
            return transfers.save(row);
        });
        audit.record(AUDIT_SENT, "B2B_TRANSFER", after.getUuid().toString(), null, Map.of(
                "approval_request_uuid", request.requestUuid().toString(), "status", after.getStatus().name(),
                "outcome_unknown", after.isOutcomeUnknown(), "amount", after.getAmount().toPlainString()));
        if (after.getStatus() == B2bTransferStatus.FAILED) {
            return new Outcome(false, NotSent.wrote(after.getResultDesc()) ? NotSent.outcome(after.getResultDesc())
                    : "Safaricom refused the sweep: " + after.getResultDesc());
        }
        return new Outcome(true, after.isOutcomeUnknown() ? UNKNOWN_OUTCOME
                : "Sent to Safaricom; the transfer settles when the B2B result arrives");
    }

    /** True unless Daraja itself answered: a refusal (4xx, or ResponseCode not 0) or nothing was sent. */
    static boolean outcomeUnknown(MpesaApiResponse<B2BTransferResponse> response) {
        if (response.success()) {
            return response.data() == null;
        }
        String code = response.error() == null ? null : response.error().errorCode();
        if (MpesaErrorResponse.NOT_SENT.equals(code) || "AUTH_FAILED".equals(code)) {
            return false;
        }
        return code == null || MpesaErrorResponse.NETWORK_ERROR.equals(code) || "INTERNAL_ERROR".equals(code)
                || response.httpStatus() >= 500 || response.httpStatus() == 408;
    }

    /** The B2B result: ResultCode 0 is success, anything else a failure (developer.safaricom.co.ke/apis/
     * BusinessPayBill). The row is locked and only a PENDING transfer takes a result, so repeats change nothing.
     * A result nothing matches is reported to the inbox as FAILED, so it can be replayed or attached by hand. */
    @Transactional
    public Optional<B2bTransfer> applyResult(B2BResult result, String raw) {
        Optional<B2bTransfer> match = lock(result.originatorConversationId(), result.conversationId());
        if (match.isEmpty()) {
            match = fallback(ResultFacts.of(result, raw));
        }
        if (match.isEmpty()) {
            log.warn("B2B result for unknown transfer {} / {}", result.originatorConversationId(),
                    result.conversationId());
            CallbackOutcome.failed(NO_MATCH);
            return Optional.empty();
        }
        return Optional.of(settle(match.get(), result, raw));
    }

    /** Finance attaches a stored result nothing matched to the transfer it belongs to, after checking. */
    @Transactional
    public B2bTransfer attachResult(UUID transferUuid, B2BResult result, String raw) {
        B2bTransfer transfer = transfers.lockByUuid(transferUuid)
                .orElseThrow(() -> new NotFoundException("B2B transfer not found"));
        if (transfer.getStatus() != B2bTransferStatus.PENDING && !closedByHand(transfer)) {
            throw new ConflictException("This transfer is already " + transfer.getStatus() + "; nothing to attach.");
        }
        if (differs(transfer.getOriginatorConversationId(), result.originatorConversationId())
                || differs(transfer.getConversationId(), result.conversationId())) {
            throw new ConflictException("The result names another conversation than this transfer.");
        }
        ResultFacts facts = ResultFacts.of(result, raw);
        if (facts.amount() != null && facts.amount().compareTo(transfer.getAmount()) != 0) {
            throw new ConflictException("The result is for KES " + facts.amount().toPlainString() + ", not the "
                    + "transfer's KES " + transfer.getAmount().toPlainString() + ".");
        }
        if (facts.receiver() != null && !facts.receiver().equals(transfer.getReceiverShortcode())) {
            throw new ConflictException("The result names receiver " + facts.receiver() + ", not "
                    + transfer.getReceiverShortcode() + ".");
        }
        return settle(transfer, result, raw);
    }

    /** Whether the result's amount and receiver, where it names them, agree with the transfer. */
    public boolean fits(B2bTransfer transfer, B2BResult result) {
        ResultFacts facts = ResultFacts.of(result, null);
        return (facts.amount() == null || facts.amount().compareTo(transfer.getAmount()) == 0)
                && (facts.receiver() == null || facts.receiver().equals(transfer.getReceiverShortcode()))
                && (facts.amount() != null || facts.receiver() != null);
    }

    /** Open transfers without Safaricom ids from the last FALLBACK_WINDOW, newest first. */
    @Transactional(readOnly = true)
    public List<B2bTransfer> attachable() {
        return transfers.findAttachable(LocalDateTime.now().minus(FALLBACK_WINDOW), PageRequest.of(0, 50));
    }

    /** Finance closes a transfer whose outcome stayed unknown as FAILED, once the statement shows no money moved. */
    @Transactional
    public B2bTransfer closeAsFailed(UUID shortcodeUuid, UUID transferUuid, String note) {
        if (!StringUtils.hasText(note)) {
            throw new InvalidRequestException("note", "is required: say what the statement showed");
        }
        transfers.findByUuidAndShortcodeUuid(transferUuid, shortcodeUuid)
                .orElseThrow(() -> new NotFoundException("B2B transfer not found"));
        B2bTransfer transfer = transfers.lockByUuid(transferUuid).orElseThrow();
        if (transfer.getStatus() != B2bTransferStatus.PENDING || !transfer.isOutcomeUnknown()) {
            throw new ConflictException("Only a pending transfer whose outcome is unknown can be closed by hand.");
        }
        String actor = CurrentActor.name();
        transfer.setStatus(B2bTransferStatus.FAILED);
        transfer.setOutcomeUnknown(false);
        transfer.setClosedBy(actor);
        transfer.setCloseNote(truncate(note.trim(), 500));
        transfer.setResultDesc(truncate("Closed as failed by " + actor + " after checking the statement: "
                + note.trim(), 500));
        transfer.setCompletedAt(LocalDateTime.now());
        B2bTransfer saved = transfers.save(transfer);
        audit.record(AUDIT_CLOSED, "B2B_TRANSFER", saved.getUuid().toString(),
                Map.of("status", B2bTransferStatus.PENDING.name(), "outcome_unknown", true),
                Map.of("status", saved.getStatus().name(), "note", saved.getCloseNote()));
        return saved;
    }

    private B2bTransfer settle(B2bTransfer transfer, B2BResult result, String raw) {
        boolean closedByHand = closedByHand(transfer);
        if (transfer.getStatus() != B2bTransferStatus.PENDING && !closedByHand) {
            log.info("Ignoring repeat B2B result for transfer {} already {}", transfer.getUuid(), transfer.getStatus());
            return transfer;
        }
        if (closedByHand) {
            // Safaricom's answer is the truth, even after someone closed the transfer by hand.
            log.warn("B2B result for transfer {} that {} closed by hand", transfer.getUuid(), transfer.getClosedBy());
        }
        transfer.setStatus(result.isSuccessful() ? B2bTransferStatus.SUCCESS : B2bTransferStatus.FAILED);
        transfer.setOutcomeUnknown(false);
        transfer.setResultCode(result.resultCode());
        transfer.setResultDesc(truncate(result.resultDesc()));
        if (result.isSuccessful()) {
            transfer.setTransactionId(result.transactionId());
        }
        if (transfer.getConversationId() == null) {
            transfer.setConversationId(result.conversationId());
        }
        if (transfer.getOriginatorConversationId() == null) {
            transfer.setOriginatorConversationId(result.originatorConversationId());
        }
        transfer.setRawResult(raw);
        transfer.setCompletedAt(LocalDateTime.now());
        return transfers.save(transfer);
    }

    // Only when exactly one open transfer without ids fits the receiver, amount and bill reference.
    private Optional<B2bTransfer> fallback(ResultFacts facts) {
        if (facts.amount() == null || facts.receiver() == null) {
            return Optional.empty();
        }
        List<B2bTransfer> candidates = transfers.lockUnmatched(facts.receiver(), facts.amount(),
                        LocalDateTime.now().minus(FALLBACK_WINDOW)).stream()
                .filter(t -> facts.billReference() == null || t.getAccountReference() == null
                        || facts.billReference().equals(t.getAccountReference()))
                .toList();
        if (candidates.size() != 1) {
            if (candidates.size() > 1) {
                log.warn("B2B result fits {} open transfers to {}; attach it by hand", candidates.size(),
                        facts.receiver());
            }
            return Optional.empty();
        }
        log.info("Matched B2B result to transfer {} by receiver and amount", candidates.get(0).getUuid());
        return Optional.of(candidates.get(0));
    }

    private static boolean closedByHand(B2bTransfer transfer) {
        return transfer.getStatus() == B2bTransferStatus.FAILED && transfer.getClosedBy() != null
                && transfer.getRawResult() == null;
    }

    private static boolean differs(String ours, String theirs) {
        return StringUtils.hasText(ours) && StringUtils.hasText(theirs) && !ours.equals(theirs);
    }

    /** What a B2B result says about its transfer: Amount and ReceiverPartyPublicName ("000000 - Name") in
     * ResultParameters, BillReferenceNumber in ReferenceData (developer.safaricom.co.ke/apis/BusinessPayBill). */
    record ResultFacts(BigDecimal amount, String receiver, String billReference) {
        private static final Pattern LEADING_DIGITS = Pattern.compile("^\\s*(\\d{5,7})");

        static ResultFacts of(B2BResult result, String raw) {
            B2BResultParameters parameters = result.resultParameters();
            BigDecimal amount = null;
            String receiver = null;
            if (parameters != null) {
                try {
                    amount = parameters.getAmount() == null ? null : new BigDecimal(parameters.getAmount().trim());
                } catch (NumberFormatException ignored) {
                    amount = null;
                }
                String party = parameters.getReceiverPartyPublicName();
                Matcher digits = party == null ? null : LEADING_DIGITS.matcher(party);
                receiver = digits != null && digits.find() ? digits.group(1) : null;
            }
            return new ResultFacts(amount, receiver, billReference(result.referenceData()));
        }

        private static String billReference(JsonNode referenceData) {
            if (referenceData == null) {
                return null;
            }
            JsonNode items = referenceData.path("ReferenceItem");
            for (JsonNode item : items.isArray() ? items : List.of(items)) {
                if ("BillReferenceNumber".equals(item.path("Key").asText()) && item.hasNonNull("Value")) {
                    return item.get("Value").asText();
                }
            }
            return null;
        }
    }

    /** Safaricom could not process the request in time. The outcome is unknown, so it stays PENDING. */
    @Transactional
    public Optional<B2bTransfer> applyTimeout(String originatorConversationId, String conversationId,
                                              String description) {
        return lock(originatorConversationId, conversationId).map(transfer -> {
            if (transfer.getStatus() == B2bTransferStatus.PENDING && transfer.getTimedOutAt() == null) {
                transfer.setTimedOutAt(LocalDateTime.now());
                transfer.setOutcomeUnknown(true);
                transfer.setResultDesc(truncate(description != null ? description : UNKNOWN_OUTCOME));
                return transfers.save(transfer);
            }
            return transfer;
        });
    }

    private Outcome already(B2bTransfer transfer) {
        return new Outcome(transfer.getStatus() != B2bTransferStatus.FAILED,
                "Already sent as transfer " + transfer.getUuid() + " (" + transfer.getStatus() + ")");
    }

    private Optional<B2bTransfer> lock(String originatorConversationId, String conversationId) {
        Optional<B2bTransfer> byOriginator = StringUtils.hasText(originatorConversationId)
                ? transfers.lockByOriginatorConversationId(originatorConversationId) : Optional.empty();
        return byOriginator.isPresent() || !StringUtils.hasText(conversationId) ? byOriginator
                : transfers.lockByConversationId(conversationId);
    }

    private static B2bTransfer pending(Approved request, B2bDestination destination, JsonNode payload) {
        B2bTransfer transfer = new B2bTransfer();
        transfer.setShortcodeUuid(destination.getShortcodeUuid());
        transfer.setDestinationUuid(destination.getUuid());
        transfer.setApprovalRequestUuid(request.requestUuid());
        transfer.setCommandId(destination.getReceiverType().commandId());
        transfer.setReceiverShortcode(destination.getReceiverShortcode());
        transfer.setAccountReference(destination.getAccountReference());
        transfer.setAmount(request.amount());
        String reference = text(payload, "reference");
        transfer.setReference(StringUtils.hasText(reference) ? reference : B2bSweepService.reference());
        String remarks = text(payload, "remarks");
        transfer.setRemarks(StringUtils.hasText(remarks) ? remarks : "Sweep " + transfer.getReference());
        transfer.setMaker(request.maker());
        transfer.setChecker(request.checker());
        transfer.setCreatedBy(request.checker());
        return transfer;
    }

    // The checker approved what the payload shows; a destination edited since must not receive the money.
    private static boolean changed(B2bDestination destination, JsonNode payload) {
        return !Objects.equals(destination.getReceiverShortcode(), text(payload, "receiver_shortcode"))
                || !Objects.equals(destination.getReceiverType().name(), text(payload, "receiver_type"))
                || !Objects.equals(destination.getAccountReference(), text(payload, "account_reference"));
    }

    private static String text(JsonNode payload, String field) {
        if (payload == null) {
            return null;
        }
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String truncate(String value) {
        return truncate(value, 500);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
