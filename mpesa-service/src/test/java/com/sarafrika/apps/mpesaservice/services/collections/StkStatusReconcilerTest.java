package com.sarafrika.apps.mpesaservice.services.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
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

class StkStatusReconcilerTest {

    private final MpesaIncomingPaymentRepository payments = mock(MpesaIncomingPaymentRepository.class);
    private final MpesaShortCodeRepository shortcodes = mock(MpesaShortCodeRepository.class);
    private final TransactionStatusService statusService = mock(TransactionStatusService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneId.of("UTC"));

    private StkStatusReconciler reconciler(boolean enabled) {
        return new StkStatusReconciler(payments, shortcodes, statusService, enabled, Duration.ofMinutes(10), 50,
                Optional.of(clock));
    }

    @Test
    void whenDisabledTheJobDoesNothing() {
        reconciler(false).run();
        verifyNoInteractions(payments, statusService);
    }

    @Test
    void asksAboutRequestsOlderThanTheThresholdAndSurvivesFailures() {
        MpesaShortCode shortcode = new MpesaShortCode();
        shortcode.setUuid(UUID.randomUUID());
        MpesaIncomingPayment first = pending(shortcode.getUuid(), "ws_CO_1");
        MpesaIncomingPayment second = pending(shortcode.getUuid(), "ws_CO_2");
        when(payments.findByPaymentTypeAndStatusAndCreatedAtBefore(eq(IncomingPaymentType.STK_PUSH),
                eq(IncomingPaymentStatus.PENDING), eq(LocalDateTime.of(2026, 10, 26, 11, 50)), any()))
                .thenReturn(List.of(first, second));
        when(shortcodes.findByUuid(shortcode.getUuid())).thenReturn(Optional.of(shortcode));
        when(statusService.stkQuery(first, shortcode, false)).thenThrow(new IllegalStateException("down"));
        when(statusService.stkQuery(second, shortcode, false)).thenReturn(Optional.of(new TransactionStatusQuery()));

        assertThat(reconciler(true).reconcile()).isEqualTo(1);
        verify(statusService).stkQuery(second, shortcode, false);
    }

    private static MpesaIncomingPayment pending(UUID shortcodeUuid, String checkout) {
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setShortcodeUuid(shortcodeUuid);
        payment.setCheckoutRequestId(checkout);
        payment.setPaymentType(IncomingPaymentType.STK_PUSH);
        return payment;
    }
}
