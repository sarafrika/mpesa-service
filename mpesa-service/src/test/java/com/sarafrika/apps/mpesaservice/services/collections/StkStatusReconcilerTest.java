package com.sarafrika.apps.mpesaservice.services.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.TransactionStatusQuery;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class StkStatusReconcilerTest {

    private final MpesaIncomingPaymentRepository payments = mock(MpesaIncomingPaymentRepository.class);
    private final MpesaShortCodeRepository shortcodes = mock(MpesaShortCodeRepository.class);
    private final TransactionStatusService statusService = mock(TransactionStatusService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);

    private StkStatusReconciler reconciler(boolean enabled) {
        TransactionTemplate transactions = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        return new StkStatusReconciler(payments, shortcodes, statusService, transactions, enabled,
                Duration.ofMinutes(10), 50, Duration.ofHours(1), Duration.ofDays(2), Optional.of(clock));
    }

    @Test
    void whenDisabledTheJobDoesNothing() {
        reconciler(false).run();
        verifyNoInteractions(payments, statusService);
    }

    @Test
    void claimsDueRequestsPushesTheirNextCheckOutAndSurvivesFailures() {
        MpesaShortCode shortcode = new MpesaShortCode();
        shortcode.setUuid(UUID.randomUUID());
        MpesaIncomingPayment first = pending(shortcode.getUuid(), "ws_CO_1");
        MpesaIncomingPayment second = pending(shortcode.getUuid(), "ws_CO_2");
        second.setStatusChecks(3);
        when(payments.claimStkChecks(eq(NOW.minusMinutes(10)), eq(NOW.minusDays(2)), eq(NOW), anyInt()))
                .thenReturn(List.of(first, second));
        when(payments.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(shortcodes.findByUuid(shortcode.getUuid())).thenReturn(Optional.of(shortcode));
        when(statusService.stkQuery(first, shortcode, false)).thenThrow(new IllegalStateException("down"));
        when(statusService.stkQuery(second, shortcode, false)).thenReturn(Optional.of(new TransactionStatusQuery()));

        assertThat(reconciler(true).reconcile()).isEqualTo(1);
        verify(statusService).stkQuery(second, shortcode, false);
        assertThat(first.getStatusChecks()).isEqualTo(1);
        assertThat(first.getNextCheckAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(second.getNextCheckAt()).isEqualTo(NOW.plusMinutes(8));
    }

    @Test
    void theDelayDoublesUpToTheCap() {
        StkStatusReconciler job = reconciler(true);
        assertThat(job.delayAfter(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(job.delayAfter(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(job.delayAfter(7)).isEqualTo(Duration.ofHours(1));
        assertThat(job.delayAfter(500)).isEqualTo(Duration.ofHours(1));
        verifyNoInteractions(statusService);
    }

    private static MpesaIncomingPayment pending(UUID shortcodeUuid, String checkout) {
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setShortcodeUuid(shortcodeUuid);
        payment.setCheckoutRequestId(checkout);
        payment.setPaymentType(IncomingPaymentType.STK_PUSH);
        return payment;
    }
}
