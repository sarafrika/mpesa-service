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
import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import java.math.BigDecimal;
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

/** Sweeps to a B2B destination: checked here, then submitted to Approvals; nothing moves until a checker
 * approves and {@link B2bSweepExecutor} sends it. */
@Service
public class B2bSweepService {

    public static final String TYPE = "B2B_SWEEP";
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
        BigDecimal amount = request.amount();
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidRequestException("amount", "must be whole shillings");
        }
        KnownFloat known = balances.knownFloat(shortcodeUuid);
        if (known.known() && amount.compareTo(known.utility()) > 0) {
            throw new InvalidRequestException("amount", "is more than the utility balance last reported ("
                    + known.utility().toPlainString() + ")");
        }
        ApprovalGateway approvals = gateway.getIfAvailable();
        if (approvals == null) {
            throw UpstreamException.unavailable("Approvals are not available, so the sweep was not submitted");
        }
        String reference = reference();
        ObjectNode payload = payload(shortcode, destination, amount, reference, known);
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

    /** What the checker sees and the executor sends: the destination as it was when the sweep was asked for. */
    ObjectNode payload(MpesaShortCode shortcode, B2bDestination destination, BigDecimal amount, String reference,
                       KnownFloat known) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("shortcode", shortcode.getShortcode());
        node.put("business_name", shortcode.getBusinessName());
        node.put("destination_uuid", destination.getUuid().toString());
        node.put("destination_name", destination.getName());
        node.put("receiver_shortcode", destination.getReceiverShortcode());
        node.put("receiver_type", destination.getReceiverType().name());
        node.put("command_id", destination.getReceiverType().commandId());
        node.put("account_reference", destination.getAccountReference());
        node.put("amount", amount.toPlainString());
        node.put("reference", reference);
        node.put("remarks", "Sweep " + reference);
        node.put("utility_balance", known.known() ? known.utility().toPlainString() : null);
        node.put("utility_reported_at", known.reportedAt() == null ? null
                : Times.offset(known.reportedAt()).toString());
        node.put("balance_after", known.known() ? known.utility().subtract(amount).toPlainString() : null);
        node.put("low_float_threshold", balances.thresholdOf(shortcode).toPlainString());
        return node;
    }

    static String reference() {
        StringBuilder value = new StringBuilder("SWP-");
        for (int i = 0; i < 8; i++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return value.toString();
    }
}
