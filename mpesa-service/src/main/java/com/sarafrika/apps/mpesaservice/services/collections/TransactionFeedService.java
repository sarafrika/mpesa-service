package com.sarafrika.apps.mpesaservice.services.collections;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.services.shortcode.DarajaHints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.StatusQueryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TimelineEventDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TransactionDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TransactionDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.TransactionFeedEntry;
import com.sarafrika.apps.mpesaservice.models.TransactionStatusQuery;
import com.sarafrika.apps.mpesaservice.models.ReversalRequest;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReversalRequestRepository;
import com.sarafrika.apps.mpesaservice.services.approvals.reversal.ReversalService;
import com.sarafrika.apps.mpesaservice.repositories.MpesaOutgoingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.TransactionFeedRepository;
import com.sarafrika.apps.mpesaservice.repositories.TransactionStatusQueryRepository;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.StatusQueryMethod;
import com.sarafrika.apps.mpesaservice.utils.enums.StatusQueryStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** The console's transactions feed over both payment tables, and one transaction's full story. */
@Service
@RequiredArgsConstructor
public class TransactionFeedService {

    static final Set<String> TYPES = Set.of("C2B", "STK_PUSH", "B2B_EXPRESS_CHECKOUT", "B2C", "B2B");
    static final Set<String> STATUSES = Set.of("PENDING", "SUCCESS", "FAILED", "CANCELLED");

    private final TransactionFeedRepository feedRepository;
    private final MpesaIncomingPaymentRepository incomingRepository;
    private final MpesaOutgoingPaymentRepository outgoingRepository;
    private final TransactionStatusQueryRepository queryRepository;
    private final ObjectProvider<TransactionTimelineContributor> contributors;
    private final ReversalRequestRepository reversalRepository;
    private final ObjectProvider<ReversalService> reversalService;
    private final ObjectMapper objectMapper;

    /** Optional filters; q matches a receipt, checkout or conversation id exactly, or an account reference prefix. */
    public record Filter(String direction, String type, String status, UUID shortcodeUuid, String phone, String q,
                         OffsetDateTime from, OffsetDateTime to) {
    }

    @Transactional(readOnly = true)
    public Page<TransactionDto> list(Filter filter, Pageable pageable) {
        boolean full = PersonalData.visible();
        return feedRepository.findAll(matching(validated(filter)), pageable)
                .map(TransactionFeedService::toDto).map(row -> full ? row : row.masked());
    }

    @Transactional(readOnly = true)
    public TransactionDetailDto detail(UUID uuid) {
        TransactionFeedEntry entry = feedRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        List<TransactionStatusQuery> queries = queryRepository.findTop20ByPaymentUuidOrderByCreatedAtDesc(uuid);
        List<TimelineEventDto> timeline = new ArrayList<>();
        TransactionDetailDto detail;
        boolean full = PersonalData.visible();
        TransactionDto row = toDto(entry);
        if ("IN".equals(entry.getDirection())) {
            MpesaIncomingPayment p = incomingRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException("Transaction not found"));
            timeline.addAll(incomingTimeline(p));
            JsonNode raw = p.getRawCallbackData() == null ? null : objectMapper.valueToTree(p.getRawCallbackData());
            detail = new TransactionDetailDto(full ? row : row.masked(), p.getMerchantRequestId(),
                    full ? p.getFirstName() : PersonalData.firstInitial(p.getFirstName()),
                    full ? p.getMiddleName() : null, p.getLastName(), p.getInvoiceNumber(), p.getOrgAccountBalance(),
                    p.getTransactionDesc(), null, null, null, null, null, null, null, null,
                    full ? raw : PersonalData.redact(raw),
                    timeline, queries.stream().map(StatusQueryDto::from).toList(),
                    p.getShortcodeUuid() != null && (p.getPaymentType() == IncomingPaymentType.STK_PUSH
                            || TransactionStatusService.receipt(p) != null), false, null, latestReversal(uuid),
                    reversalBlocked(p) == null, reversalBlocked(p));
        } else {
            MpesaOutgoingPayment o = outgoingRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException("Transaction not found"));
            timeline.addAll(outgoingTimeline(o));
            JsonNode raw = parse(o.getRawCallbackData());
            detail = new TransactionDetailDto(full ? row : row.masked(), null, null, null, null, null, null, null,
                    o.getCommandId(), o.getInitiatorName(), o.getRemarks(), o.getOccasion(), o.getChargesPaid(),
                    o.getUtilityAccountAvailableFunds(), o.getWorkingAccountAvailableFunds(),
                    Times.offset(o.getTimedOutAt()), full ? raw : PersonalData.redact(raw), timeline,
                    queries.stream().map(StatusQueryDto::from).toList(),
                    o.getShortcodeUuid() != null && (StringUtils.hasText(o.getTransactionId())
                            || StringUtils.hasText(o.getOriginatorConversationId())),
                    o.getStatus() == OutgoingPaymentStatus.PENDING && o.isOutcomeUnknown(),
                    DarajaHints.forPayout(o.getResultCode()), null, false, null);
        }
        queries.forEach(query -> timeline.addAll(queryTimeline(query)));
        contributors.orderedStream().forEach(contributor -> timeline.addAll(contributor.eventsFor(uuid)));
        // Oldest first; steps with no time yet (still waiting) go last.
        timeline.sort(Comparator.comparing(TimelineEventDto::at, Comparator.nullsLast(Comparator.naturalOrder())));
        return detail;
    }

    private TransactionDetailDto.TransactionReversalDto latestReversal(UUID paymentUuid) {
        List<ReversalRequest> all = reversalRepository.findByPaymentUuidOrderByCreatedAtAscIdAsc(paymentUuid);
        if (all.isEmpty()) {
            return null;
        }
        ReversalRequest last = all.getLast();
        return new TransactionDetailDto.TransactionReversalDto(last.getUuid(), last.getApprovalUuid(),
                last.getStatus().name());
    }

    private String reversalBlocked(MpesaIncomingPayment payment) {
        ReversalService reversals = reversalService.getIfAvailable();
        return reversals == null ? "Reversals are not available here." : reversals.blockedReason(payment);
    }

    private List<TimelineEventDto> incomingTimeline(MpesaIncomingPayment p) {
        List<TimelineEventDto> events = new ArrayList<>();
        if (p.getPaymentType() == IncomingPaymentType.C2B) {
            if (p.getTransactionDate() != null) {
                events.add(event("paid", "Paid on M-Pesa", p.getAccountReference() == null ? null
                        : "Account " + p.getAccountReference(), p.getTransactionDate(), "info"));
            }
            events.add(event("confirmation_received", "Confirmation received from Safaricom",
                    p.getShortcodeUuid() == null ? "Shortcode " + p.getBusinessShortCode() + " is not configured here"
                            : null, p.getCreatedAt(), p.getShortcodeUuid() == null ? "warning" : "success"));
            return events;
        }
        if (p.getPaymentType() == IncomingPaymentType.B2B_EXPRESS_CHECKOUT) {
            events.add(event("result_received", "Paid by B2B Express Checkout", p.getTransactionDesc(),
                    p.getCreatedAt(), "success"));
            return events;
        }
        events.add(event("created", "STK push sent to the customer's phone",
                "Checkout " + p.getCheckoutRequestId(), p.getCreatedAt(), "info"));
        if (p.getStatus() == IncomingPaymentStatus.PENDING) {
            events.add(event("waiting", "Waiting for Safaricom's result", null, null, "warning"));
        } else {
            events.add(event("result_received", "Result: " + label(p.getStatus().name()), p.getResultDesc(),
                    p.getProcessedAt(), tone(p.getStatus().name())));
        }
        return events;
    }

    private List<TimelineEventDto> outgoingTimeline(MpesaOutgoingPayment o) {
        List<TimelineEventDto> events = new ArrayList<>();
        events.add(event("created", o.getClientId() == null ? "Payout requested" : "Payout requested by " + o.getClientId(),
                o.getClientReference() == null ? null : "Reference " + o.getClientReference(), o.getCreatedAt(), "info"));
        if (StringUtils.hasText(o.getConversationId())) {
            events.add(event("sent", "Accepted by Safaricom", "Conversation " + o.getConversationId(),
                    o.getCreatedAt(), "info"));
        }
        if (o.getTimedOutAt() != null) {
            events.add(event("timed_out", "Safaricom reported a queue timeout", "The outcome is not known yet",
                    o.getTimedOutAt(), "warning"));
        }
        if (o.getStatus() == OutgoingPaymentStatus.PENDING) {
            events.add(event("waiting", "Waiting for Safaricom's result", null, null, "warning"));
        } else {
            events.add(event("result_received", "Result: " + label(o.getStatus().name()), o.getResultDesc(),
                    o.getProcessedAt() != null ? o.getProcessedAt() : o.getUpdatedAt(), tone(o.getStatus().name())));
        }
        return events;
    }

    private List<TimelineEventDto> queryTimeline(TransactionStatusQuery query) {
        String how = query.getMethod() == StatusQueryMethod.STK_QUERY ? "STK query" : "Transaction Status";
        List<TimelineEventDto> events = new ArrayList<>();
        events.add(event("status_query", "Status asked (" + how + ")",
                query.getCreatedBy() == null ? null : "By " + query.getCreatedBy(), query.getCreatedAt(), "muted"));
        if (query.getStatus() != StatusQueryStatus.PENDING) {
            String answer = query.getTransactionStatus() != null ? query.getTransactionStatus() : query.getResultDesc();
            events.add(event("status_answer", "Status answer: " + (answer == null ? label(query.getStatus().name()) : answer),
                    query.getResultCode() == null ? null : "Result code " + query.getResultCode(),
                    query.getCompletedAt(), query.getStatus() == StatusQueryStatus.COMPLETED ? "info" : "warning"));
        }
        return events;
    }

    private static TimelineEventDto event(String kind, String title, String description, LocalDateTime at, String tone) {
        return new TimelineEventDto(kind, title, description, Times.offset(at), tone);
    }

    private static String tone(String status) {
        return switch (status) {
            case "SUCCESS" -> "success";
            case "FAILED" -> "danger";
            case "PENDING" -> "warning";
            default -> "muted";
        };
    }

    private static String label(String status) {
        return status.charAt(0) + status.substring(1).toLowerCase(Locale.ROOT);
    }

    private JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }

    static TransactionDto toDto(TransactionFeedEntry e) {
        String receipt = Objects.equals(e.getTransactionId(), e.getCheckoutRequestId()) ? null : e.getTransactionId();
        return new TransactionDto(e.getId(), e.getDirection().toLowerCase(Locale.ROOT), e.getType(), e.getStatus(),
                e.getShortcodeUuid(), e.getShortcode(), e.getBusinessName(), receipt, e.getCheckoutRequestId(),
                e.getConversationId(), e.getOriginatorConversationId(), e.getAccountReference(), e.getPhone(),
                e.getAmount(), e.getResultCode(), e.getResultDesc(), e.getClientId(), e.getCustomerName(),
                e.getC2bChannel(), Times.offset(e.getCreatedAt()), Times.offset(e.getProcessedAt()),
                Times.offset(e.getTransactionDate()));
    }

    static Filter validated(Filter f) {
        String direction = upper(f.direction());
        if (direction != null && !direction.equals("IN") && !direction.equals("OUT")) {
            throw new InvalidRequestException("direction", "must be in or out");
        }
        String type = upper(f.type());
        if (type != null && !TYPES.contains(type)) {
            throw new InvalidRequestException("type", "must be one of " + String.join(", ", TYPES.stream().sorted().toList()));
        }
        String status = upper(f.status());
        if (status != null && !STATUSES.contains(status)) {
            throw new InvalidRequestException("status",
                    "must be one of " + String.join(", ", STATUSES.stream().sorted().toList()));
        }
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw new InvalidRequestException("to", "must not be before from");
        }
        String phone = StringUtils.hasText(f.phone()) ? normalizePhone(f.phone()) : null;
        String q = StringUtils.hasText(f.q()) ? f.q().trim() : null;
        return new Filter(direction, type, status, f.shortcodeUuid(), phone, q, f.from(), f.to());
    }

    /** 0712 345 678, +254712345678 and 712345678 all become 254712345678; anything else is kept as typed. */
    static String normalizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10 && digits.startsWith("0")) {
            return "254" + digits.substring(1);
        }
        if (digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("1"))) {
            return "254" + digits;
        }
        return digits.isEmpty() ? phone.trim() : digits;
    }

    private static Specification<TransactionFeedEntry> matching(Filter f) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Map<String, Object> equal = new java.util.LinkedHashMap<>();
            equal.put("direction", f.direction());
            equal.put("type", f.type());
            equal.put("status", f.status());
            equal.put("shortcodeUuid", f.shortcodeUuid());
            equal.put("phone", f.phone());
            equal.forEach((field, value) -> {
                if (value != null) {
                    predicates.add(cb.equal(root.get(field), value));
                }
            });
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), Times.local(f.from())));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), Times.local(f.to())));
            }
            if (f.q() != null) {
                String prefix = f.q().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.equal(root.get("transactionId"), f.q()),
                        cb.equal(root.get("checkoutRequestId"), f.q()),
                        cb.equal(root.get("conversationId"), f.q()),
                        cb.equal(root.get("originatorConversationId"), f.q()),
                        cb.like(cb.lower(root.get("accountReference")), prefix, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
