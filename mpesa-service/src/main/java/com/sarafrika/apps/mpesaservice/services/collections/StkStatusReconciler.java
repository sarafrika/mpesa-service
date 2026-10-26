package com.sarafrika.apps.mpesaservice.services.collections;

import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Settles STK requests whose callback never came: after mpesa.stk.status-query-after it asks Safaricom with
 * an STK push query, then again with a doubling delay (up to status-query-max-delay) until give-up-after.
 * Each pass claims its rows with SKIP LOCKED, so every instance can run it. Off: status-query-enabled=false. */
@Component
@Slf4j
public class StkStatusReconciler {

    private final MpesaIncomingPaymentRepository paymentRepository;
    private final MpesaShortCodeRepository shortCodeRepository;
    private final TransactionStatusService statusService;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private final Duration after;
    private final int batchSize;
    private final Duration maxDelay;
    private final Duration giveUpAfter;
    private final Clock clock;

    public StkStatusReconciler(MpesaIncomingPaymentRepository paymentRepository,
                               MpesaShortCodeRepository shortCodeRepository,
                               TransactionStatusService statusService,
                               TransactionTemplate transactions,
                               @Value("${mpesa.stk.status-query-enabled:true}") boolean enabled,
                               @Value("${mpesa.stk.status-query-after:10m}") Duration after,
                               @Value("${mpesa.stk.status-query-batch:50}") int batchSize,
                               @Value("${mpesa.stk.status-query-max-delay:1h}") Duration maxDelay,
                               @Value("${mpesa.stk.status-query-give-up-after:2d}") Duration giveUpAfter,
                               Optional<Clock> clock) {
        this.paymentRepository = paymentRepository;
        this.shortCodeRepository = shortCodeRepository;
        this.statusService = statusService;
        this.transactions = transactions;
        this.enabled = enabled;
        this.after = after;
        this.batchSize = batchSize;
        this.maxDelay = maxDelay;
        this.giveUpAfter = giveUpAfter;
        this.clock = clock.orElse(Clock.systemDefaultZone());
    }

    @Scheduled(fixedDelayString = "${mpesa.stk.status-query-interval-ms:60000}",
            initialDelayString = "${mpesa.stk.status-query-interval-ms:60000}")
    public void run() {
        if (enabled) {
            reconcile();
        }
    }

    /** Returns how many requests were settled in this pass. */
    public int reconcile() {
        List<MpesaIncomingPayment> due = claim();
        int settled = 0;
        for (MpesaIncomingPayment payment : due) {
            Optional<MpesaShortCode> shortcode = shortCodeRepository.findByUuid(payment.getShortcodeUuid());
            if (shortcode.isEmpty()) {
                continue;
            }
            try {
                if (statusService.stkQuery(payment, shortcode.get(), false).isPresent()) {
                    settled++;
                }
            } catch (RuntimeException e) {
                log.warn("STK status query for {} failed: {}", payment.getCheckoutRequestId(), e.getMessage());
            }
        }
        if (!due.isEmpty()) {
            log.info("STK status job: {} asked, {} settled", due.size(), settled);
        }
        return settled;
    }

    /** Takes due rows and moves their next check out before asking, so no other pass takes them meanwhile. */
    List<MpesaIncomingPayment> claim() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MpesaIncomingPayment> rows = transactions.execute(status -> {
            List<MpesaIncomingPayment> claimed = paymentRepository.claimStkChecks(now.minus(after),
                    now.minus(giveUpAfter), now, batchSize);
            for (MpesaIncomingPayment row : claimed) {
                row.setStatusChecks(row.getStatusChecks() + 1);
                row.setNextCheckAt(now.plus(delayAfter(row.getStatusChecks())));
            }
            return paymentRepository.saveAll(claimed);
        });
        return rows == null ? List.of() : rows;
    }

    /** One minute after the first check, doubling each time, never more than maxDelay. */
    Duration delayAfter(int checks) {
        Duration delay = Duration.ofMinutes(1L << Math.min(Math.max(checks - 1, 0), 20));
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }
}
