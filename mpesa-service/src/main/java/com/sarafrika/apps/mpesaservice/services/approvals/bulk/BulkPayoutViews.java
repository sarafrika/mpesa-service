package com.sarafrika.apps.mpesaservice.services.approvals.bulk;

import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PayoutBatchDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PayoutBatchItemDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.PayoutBatch;
import com.sarafrika.apps.mpesaservice.models.PayoutBatchItem;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.PayoutBatchItemRepository;
import com.sarafrika.apps.mpesaservice.repositories.PayoutBatchRepository;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A batch with its progress, and its rows with each row's outcome taken from its B2C payout. */
@Service
@RequiredArgsConstructor
public class BulkPayoutViews {

    public static final Set<String> OUTCOMES = Set.of("QUEUED", "PENDING", "PAID", "FAILED", "UNKNOWN", "REFUSED",
            "ERROR");

    private final PayoutBatchRepository batches;
    private final PayoutBatchItemRepository items;
    private final MpesaShortCodeRepository shortcodes;

    private record Row(PayoutBatchItem item, MpesaOutgoingPayment payout, String outcome) {
    }

    @Transactional(readOnly = true)
    public PayoutBatchDto batch(UUID uuid) {
        PayoutBatch batch = batches.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Batch not found"));
        List<Row> rows = rows(batch);
        MpesaShortCode code = shortcodes.findByUuid(batch.getShortcodeUuid()).orElse(null);
        BigDecimal paid = rows.stream().filter(r -> r.outcome().equals("PAID")).map(r -> r.item().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PayoutBatchDto.Counts counts = new PayoutBatchDto.Counts(count(rows, "QUEUED"), count(rows, "PENDING"),
                count(rows, "PAID"), count(rows, "FAILED"), count(rows, "UNKNOWN"), count(rows, "REFUSED"),
                count(rows, "ERROR"));
        return new PayoutBatchDto(batch.getUuid(), batch.getApprovalUuid(), batch.getShortcodeUuid(),
                code == null ? null : code.getShortcode(), code == null ? null : code.getBusinessName(),
                batch.getTitle(), batch.getFileName(), batch.getStatus().name(), batch.getItemCount(),
                batch.getInvalidCount(), batch.getTotalAmount(), batch.getProcessedCount(), counts, paid,
                offset(batch.getStartedAt()), offset(batch.getFinishedAt()), offset(batch.getCreatedAt()));
    }

    /** Sorted by line or amount (the controller whitelists them); outcome narrows to one outcome. */
    @Transactional(readOnly = true)
    public Page<PayoutBatchItemDto> items(UUID uuid, String outcome, Pageable pageable) {
        PayoutBatch batch = batches.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Batch not found"));
        String wanted = outcome == null || outcome.isBlank() ? null : outcome.trim().toUpperCase(Locale.ROOT);
        if (wanted != null && !OUTCOMES.contains(wanted)) {
            throw new InvalidRequestException("outcome", "must be one of " + String.join(", ",
                    OUTCOMES.stream().sorted().toList()));
        }
        List<Row> rows = rows(batch).stream().filter(r -> wanted == null || r.outcome().equals(wanted))
                .sorted(comparator(pageable.getSort())).toList();
        boolean full = PersonalData.visible();
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        List<PayoutBatchItemDto> page = rows.subList(from, to).stream().map(r -> dto(r, full)).toList();
        return new PageImpl<>(page, pageable, rows.size());
    }

    private List<Row> rows(PayoutBatch batch) {
        return items.findWithPayouts(batch.getId()).stream().map(pair -> {
            PayoutBatchItem item = (PayoutBatchItem) pair[0];
            MpesaOutgoingPayment payout = (MpesaOutgoingPayment) pair[1];
            return new Row(item, payout, outcome(item, payout));
        }).toList();
    }

    static String outcome(PayoutBatchItem item, MpesaOutgoingPayment payout) {
        return switch (item.getStatus()) {
            case QUEUED, REFUSED, ERROR -> item.getStatus().name();
            case SENT -> payout == null ? "PENDING"
                    : payout.getStatus() == OutgoingPaymentStatus.SUCCESS ? "PAID"
                    : payout.getStatus() == OutgoingPaymentStatus.PENDING
                            ? (payout.isOutcomeUnknown() ? "UNKNOWN" : "PENDING")
                    : "FAILED";
        };
    }

    private static Comparator<Row> comparator(Sort sort) {
        Comparator<Row> order = null;
        for (Sort.Order o : sort) {
            Comparator<Row> next = switch (o.getProperty()) {
                case "amount" -> Comparator.comparing((Row r) -> r.item().getAmount());
                case "lineNumber" -> Comparator.comparingInt((Row r) -> r.item().getLineNumber());
                default -> Comparator.comparingLong((Row r) -> r.item().getId());
            };
            next = o.isDescending() ? next.reversed() : next;
            order = order == null ? next : order.thenComparing(next);
        }
        return order == null ? Comparator.comparingInt((Row r) -> r.item().getLineNumber()) : order;
    }

    private static PayoutBatchItemDto dto(Row row, boolean full) {
        PayoutBatchItem item = row.item();
        String phone = full ? item.getPhoneNumber() : PersonalData.maskPhone(item.getPhoneNumber());
        String name = full ? item.getName() : PersonalData.shortName(item.getName());
        String message = item.getMessage() != null ? item.getMessage()
                : row.payout() != null && row.outcome().equals("FAILED") ? row.payout().getResultDesc() : null;
        return new PayoutBatchItemDto(item.getUuid(), item.getLineNumber(), phone, name, item.getAmount(),
                item.getReference(), row.outcome(), item.getPayoutUuid(),
                row.payout() == null ? null : row.payout().getTransactionId(), message, offset(item.getProcessedAt()));
    }

    private static int count(List<Row> rows, String outcome) {
        return (int) rows.stream().filter(r -> r.outcome().equals(outcome)).count();
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
