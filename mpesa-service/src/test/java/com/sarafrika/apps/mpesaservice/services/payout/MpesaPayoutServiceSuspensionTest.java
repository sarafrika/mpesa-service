package com.sarafrika.apps.mpesaservice.services.payout;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.dtos.B2CPayoutRequest;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaOutgoingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** A suspended shortcode refuses payouts on the IllegalState path, which the controller answers 503. */
class MpesaPayoutServiceSuspensionTest {

    private final MpesaOutgoingPaymentRepository payments = mock(MpesaOutgoingPaymentRepository.class);
    private final MpesaShortCodeRepository shortcodes = mock(MpesaShortCodeRepository.class);
    private final MpesaDarajaService daraja = mock(MpesaDarajaService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final MpesaPayoutService service = new MpesaPayoutService(payments, shortcodes, daraja, transactions);

    @Test
    void aSuspendedShortcodeRefusesPayoutsBeforeAnythingIsSaved() {
        MpesaShortCode code = new MpesaShortCode();
        code.setUuid(UUID.randomUUID());
        code.setShortcode("600200");
        code.setInitiatorName("api");
        code.setIsActive(false);
        when(payments.findByClientIdAndClientReference(any(), any())).thenReturn(Optional.empty());
        when(shortcodes.findByUuid(code.getUuid())).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.initiate("elimika-backend", new B2CPayoutRequest(code.getUuid(),
                "254712345678", new BigDecimal("100"), "WD-1", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suspended");
        verifyNoInteractions(daraja, transactions);
    }

    @Test
    void anUnknownShortcodeIsStillABadRequest() {
        when(payments.findByClientIdAndClientReference(any(), any())).thenReturn(Optional.empty());
        when(shortcodes.findByUuid(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate("elimika-backend", new B2CPayoutRequest(UUID.randomUUID(),
                "254712345678", new BigDecimal("100"), "WD-2", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
