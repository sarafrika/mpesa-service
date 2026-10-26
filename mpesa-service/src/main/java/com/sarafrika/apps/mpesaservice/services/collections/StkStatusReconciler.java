package com.sarafrika.apps.mpesaservice.services.collections;

import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Settles STK requests whose callback never came: after mpesa.stk.status-query-after it asks Safaricom
 * with an STK push query. Turn off with mpesa.stk.status-query-enabled=false. */
@Component
@Slf4j
public class StkStatusReconciler {

    private final MpesaIncomingPaymentRepository paymentRepository;
    private final MpesaShortCodeRepository shortCodeRepository;
    private final TransactionStatusService statusService;
    private final boolean enabled;
    private final Duration after;
    private final int batchSize;
    private final Clock clock;

    public StkStatusReconciler(MpesaIncomingPaymentRepository paymentRepository,
                               MpesaShortCodeRepository shortCodeRepository,
                               TransactionStatusService statusService,
                               @Value("${mpesa.stk.status-query-enabled:true}") boolean enabled,
                               @Value("${mpesa.stk.status-query-after:10m}") Duration after,
                               @Value("${mpesa.stk.status-query-batch:50}") int batchSize,
                               Optional<Clock> clock) {
        this.paymentRepository = paymentRepository;
        this.shortCodeRepository = shortCodeRepository;
        this.statusService = statusService;
        this.enabled = enabled;
        this.after = after;
        this.batchSize = batchSize;
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
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(after);
        List<MpesaIncomingPayment> pending = paymentRepository.findByPaymentTypeAndStatusAndCreatedAtBefore(
                IncomingPaymentType.STK_PUSH, IncomingPaymentStatus.PENDING, cutoff,
                PageRequest.of(0, batchSize, Sort.by("createdAt", "id")));
        int settled = 0;
        for (MpesaIncomingPayment payment : pending) {
            if (payment.getCheckoutRequestId() == null || payment.getShortcodeUuid() == null) {
                continue;
            }
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
        if (!pending.isEmpty()) {
            log.info("STK status job: {} pending, {} settled", pending.size(), settled);
        }
        return settled;
    }
}
