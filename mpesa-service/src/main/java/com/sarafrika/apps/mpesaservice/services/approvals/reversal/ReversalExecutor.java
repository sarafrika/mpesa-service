package com.sarafrika.apps.mpesaservice.services.approvals.reversal;

import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.dtos.TransactionReversalResponse;
import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeCapabilities;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.ReversalRequest;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReversalRequestRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalLifecycle;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalTypes;
import com.sarafrika.apps.mpesaservice.services.approvals.ResumableExecutor;
import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReversalStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Sends an approved reversal to Daraja once. The record is SENDING before the call, so a run cut short
 * by a crash becomes OUTCOME_UNKNOWN on resume instead of a second request; Safaricom's answer settles it. */
@Slf4j
@Component
public class ReversalExecutor implements ResumableExecutor, ApprovalLifecycle {

    static final String UNKNOWN = "Safaricom did not confirm it received this reversal, so it may still happen. "
            + "Wait for the result or check the transaction status; do not raise it again.";
    static final String SENT = "Sent to Safaricom; waiting for its result.";
    static final String AUTH_FAILED = "AUTH_FAILED";
    static final String NOT_AUTHENTICATED = "We could not authenticate with Daraja (it issued no access token for "
            + "this shortcode's consumer key and secret), so nothing reached Safaricom. Check the credentials and run "
            + "the connection check, then raise the reversal again.";

    private final ReversalRequestRepository reversals;
    private final MpesaShortCodeRepository shortcodes;
    private final MpesaDarajaService daraja;
    private final TransactionTemplate tx;

    public ReversalExecutor(ReversalRequestRepository reversals, MpesaShortCodeRepository shortcodes,
                            MpesaDarajaService daraja, PlatformTransactionManager transactionManager) {
        this.reversals = reversals;
        this.shortcodes = shortcodes;
        this.daraja = daraja;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public String type() {
        return ApprovalTypes.REVERSAL;
    }

    @Override
    public Outcome execute(Approved request) {
        UUID uuid = request.requestUuid();
        Step step = tx.execute(status -> prepare(uuid));
        if (step.outcome() != null) {
            return step.outcome();
        }
        ReversalRequest reversal = step.reversal();
        MpesaApiResponse<TransactionReversalResponse> response = daraja.reverseTransaction(step.shortcode(),
                reversal.getTransactionId(), reversal.getAmount(), remarks(reversal));
        return tx.execute(status -> record(uuid, response));
    }

    private record Step(ReversalRequest reversal, MpesaShortCode shortcode, Outcome outcome) {
    }

    private Step prepare(UUID approvalUuid) {
        ReversalRequest reversal = reversals.lockByApprovalUuid(approvalUuid).orElse(null);
        if (reversal == null) {
            return new Step(null, null, new Outcome(false, "No reversal is recorded for this request."));
        }
        if (reversal.getStatus() != ReversalStatus.WAITING_APPROVAL) {
            if (reversal.getStatus() == ReversalStatus.SENDING) {
                // The last run stopped between sending and saving Safaricom's answer.
                reversal.setStatus(ReversalStatus.OUTCOME_UNKNOWN);
                reversals.save(reversal);
            }
            return new Step(reversal, null, outcomeOf(reversal));
        }
        String refusal = null;
        MpesaShortCode shortcode = shortcodes.findByUuid(reversal.getShortcodeUuid()).orElse(null);
        if (reversals.countReversedPayment(reversal.getPaymentUuid()) > 0) {
            refusal = "The payment was reversed in the meantime.";
        } else if (shortcode == null || !StringUtils.hasText(shortcode.getInitiatorName())) {
            refusal = "The paybill has no API initiator any more.";
        } else if (!ShortcodeCapabilities.has(shortcode, ShortcodeCapability.REVERSAL)) {
            refusal = ShortcodeCapabilities.refusal(shortcode, ShortcodeCapability.REVERSAL);
        }
        if (refusal != null) {
            reversal.setStatus(ReversalStatus.FAILED);
            reversal.setResultCode(MpesaErrorResponse.NOT_SENT);
            reversal.setResultDesc(refusal);
            reversals.save(reversal);
            return new Step(reversal, null, new Outcome(false, refusal));
        }
        reversal.setStatus(ReversalStatus.SENDING);
        reversal.setSentAt(Instant.now());
        reversals.save(reversal);
        return new Step(reversal, shortcode, null);
    }

    private Outcome record(UUID approvalUuid, MpesaApiResponse<TransactionReversalResponse> response) {
        ReversalRequest reversal = reversals.lockByApprovalUuid(approvalUuid).orElseThrow();
        if (reversal.getStatus() != ReversalStatus.SENDING) {
            return outcomeOf(reversal); // The result callback beat this reply.
        }
        TransactionReversalResponse data = response.data();
        if (response.success() && data != null && data.isAccepted()) {
            reversal.setStatus(ReversalStatus.SENT);
            reversal.setOriginatorConversationId(data.originatorConversationId());
            reversal.setConversationId(data.conversationId());
            reversal.setResultDesc(data.responseDescription());
        } else if (outcomeUnknown(response)) {
            log.warn("Reversal {} has no answer from Safaricom ({}); its outcome is unknown", reversal.getUuid(),
                    response.error() == null ? "empty reply" : response.error().errorCode());
            reversal.setStatus(ReversalStatus.OUTCOME_UNKNOWN);
            reversal.setResultDesc(UNKNOWN);
        } else {
            reversal.setStatus(ReversalStatus.FAILED);
            String code = response.error() == null ? null : response.error().errorCode();
            if (data == null && AUTH_FAILED.equals(code)) {
                // No token, so nothing reached the Reversal API: our side failed, not Safaricom's answer.
                reversal.setResultCode(AUTH_FAILED);
                reversal.setResultDesc(NOT_AUTHENTICATED);
            } else if (data != null) {
                reversal.setResultCode(data.responseCode());
                reversal.setResultDesc(data.responseDescription());
            } else if (response.error() != null) {
                reversal.setResultCode(CapabilityNotSupportedException.CODE.equals(code) ? MpesaErrorResponse.NOT_SENT
                        : clip(code, 20));
                reversal.setResultDesc(clip(response.error().errorMessage(), 500));
            }
            reversal.setCompletedAt(Instant.now());
        }
        return outcomeOf(reversals.save(reversal));
    }

    /** True unless Daraja itself answered: a refusal (4xx, or ResponseCode not 0) or nothing was sent. */
    static boolean outcomeUnknown(MpesaApiResponse<TransactionReversalResponse> response) {
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

    static Outcome outcomeOf(ReversalRequest reversal) {
        return switch (reversal.getStatus()) {
            case SENT, SENDING -> new Outcome(true, SENT);
            case OUTCOME_UNKNOWN -> new Outcome(true, UNKNOWN);
            case REVERSED -> new Outcome(true, "Safaricom reversed it"
                    + (reversal.getReversalTransactionId() == null ? "." : " (receipt "
                    + reversal.getReversalTransactionId() + ")."));
            case FAILED -> new Outcome(false, notSent(reversal)
                    ? "Not sent" + (reversal.getResultDesc() == null ? "." : ": " + reversal.getResultDesc())
                    : "Safaricom refused the reversal"
                    + (reversal.getResultDesc() == null ? "." : ": " + reversal.getResultDesc()));
            case CANCELLED, WAITING_APPROVAL -> new Outcome(false, "The reversal was not sent.");
        };
    }

    /** Failed on our side before anything reached Safaricom: no token, or the request could not be built. */
    static boolean notSent(ReversalRequest reversal) {
        String code = reversal.getResultCode();
        return AUTH_FAILED.equals(code) || MpesaErrorResponse.NOT_SENT.equals(code);
    }

    @Override
    public void closed(UUID approvalUuid, ApprovalStatus status) {
        reversals.lockByApprovalUuid(approvalUuid)
                .filter(r -> r.getStatus() == ReversalStatus.WAITING_APPROVAL)
                .ifPresent(r -> {
                    r.setStatus(ReversalStatus.CANCELLED);
                    r.setResultDesc(status == ApprovalStatus.REJECTED ? "Rejected by the checker" : "Expired");
                    reversals.save(r);
                });
    }

    /** Daraja takes 2-100 characters of remarks. */
    static String remarks(ReversalRequest reversal) {
        String text = "Reversal of " + reversal.getTransactionId();
        return text.length() > 100 ? text.substring(0, 100) : text;
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
