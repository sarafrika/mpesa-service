package com.sarafrika.apps.mpesaservice.services.treasury;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bSweepSubmittedDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bTransferDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.RequestB2bSweepRequest;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.B2bDestination;
import com.sarafrika.apps.mpesaservice.models.B2bTransfer;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.B2bDestinationRepository;
import com.sarafrika.apps.mpesaservice.repositories.B2bTransferRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalGateway;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalSubmission;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bReceiverType;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PreCheckDto;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Sweeps to a B2B destination, or B2C top-ups of our own B2C shortcodes: checked here, then submitted to
 * Approvals; nothing moves until a checker approves and {@link B2bSweepExecutor} sends it. Both are paid from
 * the source's Working account (developer.safaricom.co.ke/apis/BusinessPayBill, /apis/B2CAccountTopUp). */
@Service
public class B2bSweepService {

    public static final String TYPE = "B2B_SWEEP";
    static final String DARAJA_CALL = "POST /mpesa/b2b/v1/paymentrequest";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MpesaShortCodeRepository shortcodes;
    private final B2bDestinationRepository destinations;
    private final B2bTransferRepository transfers;
    private final BalanceService balances;
    private final ObjectProvider<ApprovalGateway> gateway;
    private final ObjectMapper objectMapper;

    public B2bSweepService(MpesaShortCodeRepository shortcodes, B2bDestinationRepository destinations,
                           B2bTransferRepository transfers, BalanceService balances,
                           ObjectProvider<ApprovalGateway> gateway, ObjectMapper objectMapper) {
        this.shortcodes = shortcodes;
        this.destinations = destinations;
        this.transfers = transfers;
        this.balances = balances;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public B2bSweepSubmittedDto request(UUID shortcodeUuid, RequestB2bSweepRequest request) {
        MpesaShortCode shortcode = shortcodes.findByUuid(shortcodeUuid)
                .orElseThrow(() -> new NotFoundException("Shortcode not found"));
        if (!Boolean.TRUE.equals(shortcode.getIsActive())) {
            throw new ConflictException("Shortcode " + shortcode.getShortcode() + " is not active; sweeps are refused");
        }
        if (!StringUtils.hasText(shortcode.getInitiatorName())) {
            throw new ConflictException("This shortcode has no initiator; B2B needs one. Add it under Credentials.");
        }
        B2bDestination destination = destinations.findByUuidAndShortcodeUuid(request.destinationUuid(), shortcodeUuid)
                .orElseThrow(() -> new InvalidRequestException("destination_uuid",
                        "is not a destination of this shortcode"));
        if (!destination.isActive()) {
            throw new InvalidRequestException("destination_uuid", "is inactive; activate it first");
        }
        MpesaShortCode receiver = null;
        if (destination.getReceiverType().topUp()) {
            receiver = B2bDestinationService.topUpReceiver(shortcodes, shortcodeUuid,
                    destination.getReceiverShortcode()).orElseThrow(() -> new InvalidRequestException(
                    "destination_uuid", "tops up " + destination.getReceiverShortcode() + ", which "
                    + B2bDestinationService.topUpRefusal(shortcodes, shortcodeUuid, destination.getReceiverShortcode())));
        }
        BigDecimal amount = request.amount();
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidRequestException("amount", "must be whole shillings");
        }
        ApprovalGateway approvals = gateway.getIfAvailable();
        if (approvals == null) {
            throw UpstreamException.unavailable("Approvals are not available, so the sweep was not submitted");
        }
        KnownWorking working = balances.knownWorking(shortcodeUuid);
        String reference = reference(destination.getReceiverType());
        ObjectNode payload = payload(shortcode, destination, amount, reference, working);
        describe(payload, destination, receiver, amount, working);
        UUID requestUuid = approvals.submit(new ApprovalSubmission(TYPE, shortcodeUuid, amount,
                request.reason().trim(), payload));
        return new B2bSweepSubmittedDto(requestUuid, reference, amount);
    }

    @Transactional(readOnly = true)
    public Page<B2bTransferDto> list(UUID shortcodeUuid, B2bTransferStatus status, Pageable pageable) {
        if (shortcodes.findByUuid(shortcodeUuid).isEmpty()) {
            throw new NotFoundException("Shortcode not found");
        }
        Page<B2bTransfer> page = transfers.findForShortcode(shortcodeUuid, status, pageable);
        List<UUID> ids = page.getContent().stream().map(B2bTransfer::getDestinationUuid).distinct().toList();
        Map<UUID, String> names = ids.isEmpty() ? Map.of() : destinations.findByUuidIn(ids).stream()
                .collect(Collectors.toMap(B2bDestination::getUuid, B2bDestination::getName, (a, b) -> a));
        return page.map(t -> B2bTransferDto.from(t, names.get(t.getDestinationUuid())));
    }

    /** One transfer as the console shows it, with its destination's name. */
    @Transactional(readOnly = true)
    public B2bTransferDto view(B2bTransfer transfer) {
        String name = destinations.findByUuidIn(List.of(transfer.getDestinationUuid())).stream().findFirst()
                .map(B2bDestination::getName).orElse(null);
        return B2bTransferDto.from(transfer, name);
    }

    /** What the checker sees and the executor sends: the destination as it was when the sweep was asked for. */
    ObjectNode payload(MpesaShortCode shortcode, B2bDestination destination, BigDecimal amount, String reference,
                       KnownWorking working) {
        B2bReceiverType type = destination.getReceiverType();
        ObjectNode node = objectMapper.createObjectNode();
        node.put("shortcode", shortcode.getShortcode());
        node.put("business_name", shortcode.getBusinessName());
        node.put("destination_uuid", destination.getUuid().toString());
        node.put("destination_name", destination.getName());
        node.put("receiver_shortcode", destination.getReceiverShortcode());
        node.put("receiver_type", type.name());
        node.put("command_id", type.commandId());
        node.put("account_reference", destination.getAccountReference());
        node.put("amount", amount.toPlainString());
        node.put("reference", reference);
        node.put("remarks", remarks(type, reference));
        node.put("working_balance", working.known() ? working.available().toPlainString() : null);
        node.put("working_reported_at", working.reportedAt() == null ? null
                : Times.offset(working.reportedAt()).toString());
        node.put("balance_after", working.known() ? working.available().subtract(amount).toPlainString() : null);
        return node;
    }

    /** The optional keys Approvals shows the checker: a title, the Daraja call and the pre-checks. */
    void describe(ObjectNode payload, B2bDestination destination, MpesaShortCode receiver, BigDecimal amount,
                  KnownWorking working) {
        B2bReceiverType type = destination.getReceiverType();
        String command = type.commandId();
        payload.put("title", (type.topUp() ? "Top up " + destination.getReceiverShortcode() + " with KES "
                : "Sweep KES ") + money(amount) + (type.topUp() ? " (" + destination.getName() + ")"
                : " to " + destination.getName()));
        payload.put("daraja_call", DARAJA_CALL + " · " + command);
        if (receiver != null) {
            payload.put("receiver_business_name", receiver.getBusinessName());
        }
        List<PreCheckDto> checks = new ArrayList<>();
        checks.add(PreCheckDto.ok("Destination " + destination.getName() + " (" + destination.getReceiverShortcode()
                + ", " + command + ") is active"));
        if (receiver != null) {
            checks.add(PreCheckDto.ok("Receiver " + receiver.getShortcode() + " is our active B2C shortcode"
                    + (StringUtils.hasText(receiver.getBusinessName()) ? " " + receiver.getBusinessName() : "")));
        }
        checks.add(workingCheck(amount, working));
        payload.set("pre_checks", objectMapper.valueToTree(checks));
    }

    /** B2B payments and top-ups debit the Working account; a short balance warns but does not block, since
     * Safaricom refuses an overdraw itself. */
    static PreCheckDto workingCheck(BigDecimal amount, KnownWorking working) {
        if (!working.known()) {
            return PreCheckDto.warn("No Working balance reading yet");
        }
        if (working.available().compareTo(amount) < 0) {
            return PreCheckDto.warn("Working KES " + money(working.available()) + " is below the amount; move "
                    + "funds from Utility to Working on the M-PESA org portal first");
        }
        return PreCheckDto.ok("Working KES " + money(working.available()) + " covers the amount");
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    /** SWP- for a sweep, TOP- for a top-up, then 8 characters: 12 in all, within AccountReference's 13. */
    static String reference(B2bReceiverType type) {
        StringBuilder value = new StringBuilder(type != null && type.topUp() ? "TOP-" : "SWP-");
        for (int i = 0; i < 8; i++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return value.toString();
    }

    static String reference() {
        return reference(null);
    }

    static String remarks(B2bReceiverType type, String reference) {
        return (type != null && type.topUp() ? "B2C top-up " : "Sweep ") + reference;
    }
}
