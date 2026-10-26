package com.sarafrika.apps.mpesaservice.services.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationExceptionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.StatusQueryDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.StkCandidateDto;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.ReconciliationExceptionRecord;
import com.sarafrika.apps.mpesaservice.models.ReconciliationRun;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReconciliationExceptionRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReconciliationRunRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.collections.C2BConfirmationService;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionStatusService;
import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationLedger.StkCandidate;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationAction;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A person's resolve actions on an exception (mpesa-finance, audited). Only BACKFILL adds money to the ledger,
 * idempotent on the receipt; ATTACH_STK_RECEIPT only names an STK payment's receipt; MERGE_DUPLICATE only
 * soft-deletes identical extra rows and never touches amounts. */
@Service
@RequiredArgsConstructor
public class ReconciliationResolver {

    static final String AUDIT_BACKFILL = "RECONCILIATION_BACKFILLED";
    static final String AUDIT_ATTACH = "RECONCILIATION_STK_RECEIPT_ATTACHED";
    static final String AUDIT_QUERY = "RECONCILIATION_STATUS_QUERIED";
    static final String AUDIT_MERGE = "RECONCILIATION_DUPLICATE_MERGED";
    static final String AUDIT_RESOLVE = "RECONCILIATION_MARKED_RESOLVED";
    static final String ENTITY = "RECONCILIATION_EXCEPTION";

    private final ReconciliationExceptionRepository exceptions;
    private final ReconciliationRunRepository runs;
    private final MpesaShortCodeRepository shortcodes;
    private final ReconciliationLedger ledger;
    private final C2BConfirmationService confirmations;
    private final TransactionStatusService statusService;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final StkCandidates stkCandidates;
    private final MpesaIncomingPaymentRepository incoming;

    /** Stores the missing deposit as a C2B payment of the statement amount shown (paid in less withdrawn over the
     * receipt's lines) and publishes c2b.confirmed; a second call changes nothing. Refused while a settled STK
     * payment without a receipt may be this deposit: that one gets the receipt attached instead. */
    @Transactional
    public ReconciliationExceptionDto backfill(UUID uuid) {
        ReconciliationExceptionRecord e = lockOpen(uuid);
        require(e, ReconciliationAction.BACKFILL, "Only a deposit missing from our ledger can be backfilled");
        ReconciliationRun run = runs.findById(e.getRunId()).orElseThrow();
        MpesaShortCode shortcode = shortcodes.findByUuid(e.getShortcodeUuid())
                .orElseThrow(() -> new ConflictException("This exception's shortcode no longer exists"));
        StatementLine line = line(e);
        BigDecimal amount = e.getStatementAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new ConflictException("The statement line for " + e.getReceipt() + " has no amount paid in");
        }
        List<StkCandidate> candidates = stkCandidates.find(shortcode.getUuid(), line, amount);
        if (!candidates.isEmpty()) {
            throw new ConflictException(candidates.size() == 1
                    ? "STK payment " + candidates.getFirst().checkoutRequestId() + " of this amount settled without a "
                    + "receipt around this time; attach the receipt to it instead of adding a second deposit"
                    : candidates.size() + " STK payments of this amount settled without a receipt around this time; "
                    + "attach the receipt to the right one instead of adding a second deposit");
        }
        String source = run.getSource() == ReconciliationSource.PULL_TRANSACTIONS ? "PULL_TRANSACTIONS" : "STATEMENT";
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("Source", source.equals("STATEMENT") ? "Organisation statement" : "Pull Transactions");
        raw.put("ReconciliationRun", run.getUuid().toString());
        raw.put("StatementLine", line.raw());
        C2BConfirmationService.Confirmation confirmation =
                confirmations.confirm(StatementBackfill.callback(line, shortcode.getShortcode(), amount), raw, source);
        if (!confirmation.duplicate()) {
            ledger.markSource(confirmation.payment().getUuid(), source);
        }
        e.setLedgerPaymentUuid(confirmation.payment().getUuid());
        e.setLedgerType("C2B");
        e.setLedgerStatus(confirmation.payment().getStatus() == null ? null : confirmation.payment().getStatus().name());
        e.setLedgerAmount(confirmation.payment().getAmount());
        resolve(e, ReconciliationAction.BACKFILL, confirmation.duplicate()
                ? "Already in the ledger; nothing was added" : "Backfilled from Safaricom's record as a C2B payment");
        audit.record(AUDIT_BACKFILL, ENTITY, uuid.toString(), null, Map.of("receipt", e.getReceipt(),
                "payment_uuid", confirmation.payment().getUuid().toString(), "already_stored", confirmation.duplicate(),
                "source", source));
        return dto(exceptions.save(e));
    }

    /** The settled STK payments without a receipt that this missing deposit may be, oldest request first. */
    @Transactional(readOnly = true)
    public List<StkCandidateDto> listStkCandidates(UUID uuid) {
        ReconciliationExceptionRecord e = exceptions.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Reconciliation exception not found"));
        if (e.getStatus() != ReconciliationExceptionStatus.OPEN || e.getStatementLine() == null
                || e.getLedgerPaymentUuid() != null
                || !ReconciliationExceptionRecord.IN.equals(e.getDirection())) {
            return List.of();
        }
        return stkCandidates.find(e.getShortcodeUuid(), line(e), e.getStatementAmount()).stream()
                .map(StkCandidateDto::from).toList();
    }

    /** Gives a settled STK payment that has no receipt the statement's receipt (and time, when it had none). No
     * money is added and nothing is announced: the product heard of this payment when it settled. */
    @Transactional
    public ReconciliationExceptionDto attachStkReceipt(UUID uuid, UUID paymentUuid) {
        ReconciliationExceptionRecord e = lockOpen(uuid);
        require(e, ReconciliationAction.ATTACH_STK_RECEIPT, "Only a deposit missing from our ledger can be attached");
        StatementLine line = line(e);
        StkCandidate candidate = stkCandidates.find(e.getShortcodeUuid(), line, e.getStatementAmount()).stream()
                .filter(c -> c.uuid().equals(paymentUuid)).findFirst()
                .orElseThrow(() -> new ConflictException("That payment is not a settled STK payment without a receipt "
                        + "of this amount around this time"));
        incoming.findByTransactionId(e.getReceipt()).ifPresent(other -> {
            throw new ConflictException("Receipt " + e.getReceipt() + " is already on payment " + other.getUuid()
                    + "; mark this exception resolved instead");
        });
        MpesaIncomingPayment payment = incoming.lockByCheckoutRequestId(candidate.checkoutRequestId())
                .filter(p -> p.getUuid().equals(paymentUuid))
                .orElseThrow(() -> new ConflictException("That STK payment no longer exists"));
        if (payment.getStatus() != IncomingPaymentStatus.SUCCESS
                || !Objects.equals(payment.getTransactionId(), payment.getCheckoutRequestId())) {
            throw new ConflictException("That STK payment already has a receipt or is not settled");
        }
        ReconciliationRun run = runs.findById(e.getRunId()).orElseThrow();
        Map<String, Object> raw = new LinkedHashMap<>(payment.getRawCallbackData() == null ? Map.of()
                : payment.getRawCallbackData());
        Map<String, Object> attached = new LinkedHashMap<>();
        attached.put("Receipt", e.getReceipt());
        attached.put("Source", run.getSource() == ReconciliationSource.PULL_TRANSACTIONS ? "Pull Transactions"
                : "Organisation statement");
        attached.put("ReconciliationRun", run.getUuid().toString());
        attached.put("AttachedBy", CurrentActor.name());
        raw.put("ReceiptFromReconciliation", attached);
        payment.setRawCallbackData(raw);
        payment.setTransactionId(e.getReceipt());
        if (payment.getTransactionDate() == null) {
            payment.setTransactionDate(line.completedAt());
        }
        incoming.save(payment);
        e.setLedgerPaymentUuid(payment.getUuid());
        e.setLedgerType(payment.getPaymentType().name());
        e.setLedgerStatus(payment.getStatus().name());
        e.setLedgerAmount(payment.getAmount());
        e.setLedgerCount(1);
        resolve(e, ReconciliationAction.ATTACH_STK_RECEIPT, "Receipt attached to STK payment "
                + payment.getCheckoutRequestId() + "; nothing was added or announced");
        audit.record(AUDIT_ATTACH, ENTITY, uuid.toString(),
                Map.of("payment_uuid", payment.getUuid().toString(), "transaction_id", payment.getCheckoutRequestId()),
                Map.of("payment_uuid", payment.getUuid().toString(), "transaction_id", e.getReceipt()));
        return dto(exceptions.save(e));
    }

    /** Sends a status query for our transaction; the exception stays open until someone resolves it. */
    @Transactional
    public ReconciliationExceptionDto queryStatus(UUID uuid) {
        ReconciliationExceptionRecord e = lockOpen(uuid);
        require(e, ReconciliationAction.QUERY_STATUS, "There is no transaction of ours to ask Safaricom about");
        StatusQueryDto query = statusService.request(e.getLedgerPaymentUuid());
        e.setLastAction(ReconciliationAction.QUERY_STATUS);
        e.setStatusQueryUuid(query.uuid());
        audit.record(AUDIT_QUERY, ENTITY, uuid.toString(), null, Map.of("receipt", e.getReceipt(),
                "query_uuid", query.uuid().toString()));
        return dto(exceptions.save(e));
    }

    /** Keeps the first-saved row and soft-deletes the others, only when every row is the same money. */
    @Transactional
    public ReconciliationExceptionDto mergeDuplicate(UUID uuid) {
        ReconciliationExceptionRecord e = lockOpen(uuid);
        require(e, ReconciliationAction.MERGE_DUPLICATE, "Only a duplicate in our ledger can be merged");
        MpesaShortCode shortcode = shortcodes.findByUuid(e.getShortcodeUuid())
                .orElseThrow(() -> new ConflictException("This exception's shortcode no longer exists"));
        List<LedgerEntry> rows = ledger.byReceipts(shortcode.getUuid(), shortcode.getShortcode(),
                List.of(e.getReceipt()), false).stream().sorted(ReconciliationMatcher.FIRST).toList();
        if (rows.size() < 2) {
            throw new ConflictException("Receipt " + e.getReceipt() + " is no longer duplicated; mark it resolved");
        }
        LedgerEntry keep = rows.getFirst();
        boolean same = rows.stream().allMatch(r -> !LedgerEntry.B2B.equals(r.table())
                && !LedgerEntry.PRODUCT.equals(r.table())
                && r.table().equals(keep.table()) && r.amount().compareTo(keep.amount()) == 0
                && r.status().equals(keep.status()));
        if (!same) {
            throw new ConflictException("These rows differ in amount, direction or status; review them and mark the "
                    + "exception resolved instead of merging");
        }
        List<String> removed = rows.subList(1, rows.size()).stream().map(r -> {
            ledger.softDelete(r.table(), r.uuid(), CurrentActor.name());
            return r.uuid().toString();
        }).toList();
        e.setLedgerCount(1);
        e.setLedgerPaymentUuid(keep.uuid());
        resolve(e, ReconciliationAction.MERGE_DUPLICATE, "Kept " + keep.uuid() + "; removed " + removed.size()
                + (removed.size() == 1 ? " duplicate" : " duplicates"));
        audit.record(AUDIT_MERGE, ENTITY, uuid.toString(), Map.of("rows", rows.stream()
                .map(r -> r.uuid().toString()).toList()), Map.of("kept", keep.uuid().toString(), "removed", removed));
        return dto(exceptions.save(e));
    }

    @Transactional
    public ReconciliationExceptionDto markResolved(UUID uuid, String note) {
        ReconciliationExceptionRecord e = lockOpen(uuid);
        resolve(e, ReconciliationAction.MARK_RESOLVED, note.trim());
        audit.record(AUDIT_RESOLVE, ENTITY, uuid.toString(), null, Map.of("receipt", e.getReceipt(),
                "kind", e.getKind().name(), "note", note.trim()));
        return dto(exceptions.save(e));
    }

    private ReconciliationExceptionRecord lockOpen(UUID uuid) {
        ReconciliationExceptionRecord e = exceptions.lockByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Reconciliation exception not found"));
        if (e.getStatus() != ReconciliationExceptionStatus.OPEN) {
            throw new ConflictException("This exception is already " + e.getStatus().name().toLowerCase());
        }
        return e;
    }

    private StatementLine line(ReconciliationExceptionRecord e) {
        try {
            return objectMapper.readValue(e.getStatementLine(), StatementLine.class);
        } catch (Exception ex) {
            throw new ConflictException("The statement line for " + e.getReceipt() + " could not be read back");
        }
    }

    // Backfill and attach answer the same exception: which one is offered depends on the STK payments the run
    // found, and each checks them again (backfill refuses while one fits, attach needs the one chosen).
    private static void require(ReconciliationExceptionRecord e, ReconciliationAction action, String message) {
        List<ReconciliationAction> allowed = ReconciliationService.allowedActions(e);
        boolean either = (action == ReconciliationAction.BACKFILL || action == ReconciliationAction.ATTACH_STK_RECEIPT)
                && (allowed.contains(ReconciliationAction.BACKFILL)
                || allowed.contains(ReconciliationAction.ATTACH_STK_RECEIPT));
        if (!allowed.contains(action) && !either) {
            throw new ConflictException(e.getReversedBy() != null && action == ReconciliationAction.BACKFILL
                    ? "Receipt " + e.getReceipt() + " was reversed at Safaricom by " + e.getReversedBy()
                    + "; there is nothing to backfill" : message);
        }
    }

    private static void resolve(ReconciliationExceptionRecord e, ReconciliationAction action, String note) {
        e.setStatus(ReconciliationExceptionStatus.RESOLVED);
        e.setLastAction(action);
        e.setResolutionNote(ReconciliationService.truncate(note, 1000));
        e.setResolvedBy(CurrentActor.name());
        e.setResolvedAt(LocalDateTime.now());
    }

    private ReconciliationExceptionDto dto(ReconciliationExceptionRecord e) {
        UUID runUuid = runs.findById(e.getRunId()).map(ReconciliationRun::getUuid).orElse(null);
        return ReconciliationService.toDto(e, runUuid);
    }
}
