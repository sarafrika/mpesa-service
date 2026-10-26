package com.sarafrika.apps.mpesaservice.services.approvals.reversal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PreCheckDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.RequestReversalRequest;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.ReversalRequest;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaOutgoingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReversalRequestRepository;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalGateway;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalSubmission;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalTypes;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReversalStatus;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Raises a REVERSAL approval for a received payment after checking it can be reversed: settled, with a
 * receipt, not reversed or being reversed, and for no more than it was. */
@Service
@RequiredArgsConstructor
public class ReversalService {

    static final String DARAJA_CALL = "POST /mpesa/reversal/v1/request · TransactionReversal";
    static final Set<ReversalStatus> CLOSED = Set.of(ReversalStatus.CANCELLED, ReversalStatus.FAILED);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final MpesaIncomingPaymentRepository payments;
    private final MpesaOutgoingPaymentRepository payouts;
    private final MpesaShortCodeRepository shortcodes;
    private final ReversalRequestRepository reversals;
    private final ApprovalGateway gateway;
    private final ObjectMapper mapper;

    @Transactional
    public UUID request(RequestReversalRequest request) {
        MpesaIncomingPayment payment = payments.findByUuid(request.transactionUuid()).orElse(null);
        if (payment == null) {
            if (payouts.findByUuid(request.transactionUuid()).isPresent()) {
                throw new ConflictException("Only money received can be reversed; this is a payout.");
            }
            throw new NotFoundException("Transaction not found");
        }
        String blocked = blockedReason(payment);
        if (blocked != null) {
            throw new ConflictException(blocked);
        }
        BigDecimal original = payment.getAmount();
        BigDecimal amount = request.amount() == null ? original : request.amount();
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidRequestException("amount", "must be whole shillings");
        }
        if (amount.compareTo(original) > 0) {
            throw new InvalidRequestException("amount", "must not be more than the original KES " + money(original));
        }
        MpesaShortCode shortcode = shortcodes.findByUuid(payment.getShortcodeUuid()).orElseThrow();

        List<PreCheckDto> checks = new ArrayList<>();
        String settled = payment.getTransactionDate() != null ? payment.getTransactionDate().format(DAY)
                : payment.getCreatedAt() == null ? "" : payment.getCreatedAt().format(DAY);
        checks.add(PreCheckDto.ok("Original payment " + payment.getTransactionId() + " found and settled"
                + (settled.isEmpty() ? "" : " on " + settled)));
        checks.add(PreCheckDto.ok("Not reversed before and no other reversal pending"));
        checks.add(amount.compareTo(original) == 0
                ? PreCheckDto.ok("Reverses the full KES " + money(original))
                : PreCheckDto.warn("Reverses KES " + money(amount) + " of KES " + money(original)
                        + "; Safaricom's documentation does not say whether it accepts a partial amount"));

        ObjectNode payload = mapper.createObjectNode();
        payload.put("title", "Reverse " + payment.getTransactionId());
        payload.put("daraja_call", DARAJA_CALL);
        payload.set("pre_checks", mapper.valueToTree(checks));
        payload.put("transaction_uuid", payment.getUuid().toString());
        payload.put("receipt", payment.getTransactionId());
        payload.put("original_amount", original);
        payload.put("payment_type", payment.getPaymentType() == null ? null : payment.getPaymentType().name());
        payload.put("account_reference", payment.getAccountReference());
        UUID approval = gateway.submit(new ApprovalSubmission(ApprovalTypes.REVERSAL, shortcode.getUuid(), amount,
                request.reason().trim(), payload));

        ReversalRequest reversal = new ReversalRequest();
        reversal.setApprovalUuid(approval);
        reversal.setPaymentUuid(payment.getUuid());
        reversal.setShortcodeUuid(shortcode.getUuid());
        reversal.setTransactionId(payment.getTransactionId());
        reversal.setAmount(amount);
        reversal.setOriginalAmount(original);
        reversal.setRequestedBy(CurrentActor.name());
        try {
            reversals.saveAndFlush(reversal);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("This payment already has a reversal waiting for approval or in progress.");
        }
        return approval;
    }

    /** Why this received payment cannot be reversed now, or null when a reversal may be requested. */
    @Transactional(readOnly = true)
    public String blockedReason(MpesaIncomingPayment payment) {
        if (payment.getStatus() != IncomingPaymentStatus.SUCCESS) {
            return "Only a successful payment can be reversed; this one is "
                    + payment.getStatus().name().toLowerCase(Locale.ROOT) + ".";
        }
        if (!StringUtils.hasText(payment.getTransactionId())) {
            return "This payment has no M-Pesa receipt, so Safaricom cannot reverse it.";
        }
        if (reversals.countReversedPayment(payment.getUuid()) > 0) {
            return "This payment has already been reversed.";
        }
        if (reversals.existsByPaymentUuidAndStatusNotIn(payment.getUuid(), CLOSED)) {
            return "This payment already has a reversal waiting for approval or in progress.";
        }
        MpesaShortCode shortcode = payment.getShortcodeUuid() == null ? null
                : shortcodes.findByUuid(payment.getShortcodeUuid()).orElse(null);
        if (shortcode == null) {
            return "The payment's paybill is not configured here, so it cannot be reversed.";
        }
        if (!StringUtils.hasText(shortcode.getInitiatorName())) {
            return "Paybill " + shortcode.getShortcode() + " has no API initiator; Safaricom's Reversal API needs "
                    + "one with the Org Reversals Initiator role.";
        }
        return null;
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }
}
