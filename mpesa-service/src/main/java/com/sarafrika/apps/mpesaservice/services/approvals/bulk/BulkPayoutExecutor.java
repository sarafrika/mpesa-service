package com.sarafrika.apps.mpesaservice.services.approvals.bulk;

import com.sarafrika.apps.mpesaservice.dtos.B2CPayoutRequest;
import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.PayoutBatch;
import com.sarafrika.apps.mpesaservice.models.PayoutBatchItem;
import com.sarafrika.apps.mpesaservice.repositories.ApprovalRequestRepository;
import com.sarafrika.apps.mpesaservice.repositories.PayoutBatchItemRepository;
import com.sarafrika.apps.mpesaservice.repositories.PayoutBatchRepository;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalLifecycle;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalProperties;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalTypes;
import com.sarafrika.apps.mpesaservice.services.approvals.ResumableExecutor;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.PayoutBatchItemStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.PayoutBatchStatus;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Pays a batch one row at a time through MpesaPayoutService under the console's client id, with the row's
 * reference as client_reference. A row already sent is never sent again (the payout service returns the
 * payout it has for that reference, unknown outcome or not), so a resumed batch picks up where it stopped. */
@Slf4j
@Component
public class BulkPayoutExecutor implements ResumableExecutor, ApprovalLifecycle {

    private static final List<PayoutBatchItemStatus> TO_SEND = List.of(PayoutBatchItemStatus.QUEUED,
            PayoutBatchItemStatus.ERROR);

    private final PayoutBatchRepository batches;
    private final PayoutBatchItemRepository items;
    private final ApprovalRequestRepository approvals;
    private final MpesaPayoutService payouts;
    private final ApprovalProperties properties;
    private final TransactionTemplate tx;

    public BulkPayoutExecutor(PayoutBatchRepository batches, PayoutBatchItemRepository items,
                              ApprovalRequestRepository approvals, MpesaPayoutService payouts,
                              ApprovalProperties properties, PlatformTransactionManager transactionManager) {
        this.batches = batches;
        this.items = items;
        this.approvals = approvals;
        this.payouts = payouts;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public String type() {
        return ApprovalTypes.BULK_PAYOUT;
    }

    @Override
    public Outcome execute(Approved request) {
        PayoutBatch batch = tx.execute(status -> batches.lockByApprovalUuid(request.requestUuid()).map(b -> {
            if (b.getStatus() == PayoutBatchStatus.WAITING) {
                b.setStatus(PayoutBatchStatus.RUNNING);
                b.setStartedAt(Instant.now());
                return batches.save(b);
            }
            return b;
        }).orElse(null));
        if (batch == null) {
            return new Outcome(false, "No batch is recorded for this request.");
        }
        if (batch.getStatus() == PayoutBatchStatus.RUNNING) {
            for (PayoutBatchItem item : items.findByBatchIdAndStatusInOrderByLineNumberAsc(batch.getId(), TO_SEND)) {
                send(batch, item);
                tx.executeWithoutResult(status -> {
                    batches.findById(batch.getId()).ifPresent(b -> {
                        b.setProcessedCount((int) items.countByBatchIdAndStatusNot(b.getId(),
                                PayoutBatchItemStatus.QUEUED));
                        batches.save(b);
                    });
                    approvals.touch(request.requestUuid(), Instant.now());
                });
            }
            tx.executeWithoutResult(status -> batches.findById(batch.getId()).ifPresent(b -> {
                b.setStatus(PayoutBatchStatus.COMPLETED);
                b.setFinishedAt(Instant.now());
                b.setProcessedCount(b.getItemCount());
                batches.save(b);
                items.releaseReferences(b.getId());
            }));
        }
        return summary(batch.getId());
    }

    private void send(PayoutBatch batch, PayoutBatchItem item) {
        B2CPayoutRequest payout = new B2CPayoutRequest(batch.getShortcodeUuid(), item.getPhoneNumber(),
                item.getAmount(), item.getReference(), remarks(batch.getTitle()), null);
        PayoutBatchItemStatus status;
        UUID payoutUuid = null;
        String message;
        try {
            MpesaOutgoingPayment sent = payouts.initiate(properties.consoleClientId(), payout);
            boolean older = sent.getCreatedAt() != null && batch.getCreatedAt() != null && sent.getCreatedAt()
                    .isBefore(batch.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime());
            if (older) {
                // The reference was used before this batch existed: that payout is not this row's.
                status = PayoutBatchItemStatus.REFUSED;
                message = "Reference already used by an earlier payout; not sent";
            } else if (!sameRow(item, sent)) {
                // The payout for this reference pays another phone or amount, so it is not this row's.
                status = PayoutBatchItemStatus.REFUSED;
                message = "Reference already used by another payout to a different phone or amount; not sent";
            } else {
                status = PayoutBatchItemStatus.SENT;
                payoutUuid = sent.getUuid();
                message = sent.getStatus() == OutgoingPaymentStatus.FAILED || sent.isOutcomeUnknown()
                        ? sent.getResultDesc() : null;
            }
        } catch (IllegalArgumentException | IllegalStateException | CapabilityNotSupportedException ex) {
            status = PayoutBatchItemStatus.REFUSED;
            message = ex.getMessage();
        } catch (RuntimeException ex) {
            // Retried on resume; the payout service would return any payout already saved for the reference.
            log.error("Bulk payout item {} of batch {} failed", item.getLineNumber(), batch.getUuid(), ex);
            status = PayoutBatchItemStatus.ERROR;
            message = "Could not be sent: " + ex.getMessage();
        }
        PayoutBatchItemStatus result = status;
        UUID resultPayout = payoutUuid;
        String resultMessage = message == null || message.length() <= 500 ? message : message.substring(0, 500);
        tx.executeWithoutResult(s -> items.findById(item.getId()).ifPresent(saved -> {
            saved.setStatus(result);
            saved.setPayoutUuid(resultPayout);
            saved.setMessage(resultMessage);
            saved.setProcessedAt(Instant.now());
            items.save(saved);
        }));
    }

    static boolean sameRow(PayoutBatchItem item, MpesaOutgoingPayment payout) {
        return item.getPhoneNumber().equals(payout.getRecipientPhoneNumber()) && payout.getAmount() != null
                && item.getAmount().compareTo(payout.getAmount()) == 0;
    }

    private Outcome summary(Long batchId) {
        List<Object[]> rows = items.findWithPayouts(batchId);
        // A row whose payout failed at once (a Daraja refusal or no token) never went out, so it is not "sent".
        long failed = rows.stream().filter(r -> ((PayoutBatchItem) r[0]).getStatus() == PayoutBatchItemStatus.SENT
                && r[1] instanceof MpesaOutgoingPayment p && p.getStatus() == OutgoingPaymentStatus.FAILED
                && !p.isOutcomeUnknown()).count();
        long sent = rows.stream().filter(r -> ((PayoutBatchItem) r[0]).getStatus() == PayoutBatchItemStatus.SENT)
                .count() - failed;
        long refused = rows.stream().filter(r -> ((PayoutBatchItem) r[0]).getStatus() == PayoutBatchItemStatus.REFUSED)
                .count();
        long errors = rows.stream().filter(r -> ((PayoutBatchItem) r[0]).getStatus() == PayoutBatchItemStatus.ERROR)
                .count();
        String message = sent + " of " + rows.size() + " payouts sent to Safaricom"
                + (failed > 0 ? "; " + failed + " failed" : "")
                + (refused > 0 ? "; " + refused + " refused" : "")
                + (errors > 0 ? "; " + errors + " could not be sent" : "") + ". Each payout settles when its "
                + "result arrives.";
        return new Outcome(sent > 0, message);
    }

    @Override
    public void closed(UUID approvalUuid, ApprovalStatus status) {
        batches.lockByApprovalUuid(approvalUuid).filter(b -> b.getStatus() == PayoutBatchStatus.WAITING)
                .ifPresent(b -> {
                    b.setStatus(status == ApprovalStatus.REJECTED ? PayoutBatchStatus.REJECTED : PayoutBatchStatus.EXPIRED);
                    batches.save(b);
                    items.releaseReferences(b.getId());
                });
    }

    /** Daraja shows Remarks on the statement; the payout service falls back to "Payout" when blank. */
    static String remarks(String title) {
        if (title == null || title.trim().length() < 2) {
            return null;
        }
        String text = title.trim();
        return text.length() > 100 ? text.substring(0, 100) : text;
    }
}
