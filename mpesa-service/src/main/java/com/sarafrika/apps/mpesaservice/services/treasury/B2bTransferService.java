package com.sarafrika.apps.mpesaservice.services.treasury;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import java.math.BigDecimal;
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
            return new Outcome(false, "Safaricom refused the sweep: " + after.getResultDesc());
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
     * BusinessPayBill). The row is locked and only a PENDING transfer takes a result, so repeats change nothing. */
    @Transactional
    public Optional<B2bTransfer> applyResult(B2BResult result, String raw) {
        Optional<B2bTransfer> match = lock(result.originatorConversationId(), result.conversationId());
        if (match.isEmpty()) {
            log.warn("B2B result for unknown transfer {} / {}", result.originatorConversationId(),
                    result.conversationId());
            return Optional.empty();
        }
        B2bTransfer transfer = match.get();
        if (transfer.getStatus() != B2bTransferStatus.PENDING) {
            log.info("Ignoring repeat B2B result for transfer {} already {}", transfer.getUuid(), transfer.getStatus());
            return match;
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
        transfer.setRawResult(raw);
        transfer.setCompletedAt(LocalDateTime.now());
        return Optional.of(transfers.save(transfer));
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
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
