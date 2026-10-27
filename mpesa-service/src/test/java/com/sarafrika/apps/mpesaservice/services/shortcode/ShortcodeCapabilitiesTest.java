package com.sarafrika.apps.mpesaservice.services.shortcode;

import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.ACCOUNT_BALANCE;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.B2B_EXPRESS_CHECKOUT;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.B2C_PAYOUT;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.C2B_REGISTER;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.POCHI;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.QR;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.REVERSAL;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.STK_PUSH;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.TRANSACTION_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** The capability table from SHORTCODE-DESIGN.md, one row per type. */
class ShortcodeCapabilitiesTest {

    @Test
    void eachTypeHasExactlyTheDesignedCapabilities() {
        assertThat(ShortcodeCapabilities.of(ShortcodeType.PAYBILL)).contains(C2B_REGISTER, STK_PUSH, QR, REVERSAL,
                B2B_EXPRESS_CHECKOUT).doesNotContain(B2C_PAYOUT, POCHI);
        assertThat(ShortcodeCapabilities.of(ShortcodeType.TILL)).contains(C2B_REGISTER, STK_PUSH, QR, REVERSAL)
                .doesNotContain(B2C_PAYOUT, POCHI, B2B_EXPRESS_CHECKOUT);
        assertThat(ShortcodeCapabilities.of(ShortcodeType.B2C))
                .containsExactlyInAnyOrder(B2C_PAYOUT, POCHI, TRANSACTION_STATUS, ACCOUNT_BALANCE);
        assertThat(ShortcodeCapabilities.of(ShortcodeType.ONE_ACCOUNT))
                .isEqualTo(EnumSet.allOf(ShortcodeCapability.class));
    }

    @Test
    void aRefusalNamesTheTypeAndASwitchNeedsItsCapability() {
        MpesaShortCode till = new MpesaShortCode();
        till.setShortcode("5123456");
        till.setShortcodeType(ShortcodeType.TILL);

        assertThatThrownBy(() -> ShortcodeCapabilities.require(till, B2C_PAYOUT))
                .isInstanceOfSatisfying(CapabilityNotSupportedException.class,
                        e -> assertThat(e.getCapability()).isEqualTo(B2C_PAYOUT))
                .hasMessage("Shortcode 5123456 is a till (Buy Goods), so it cannot do B2C payouts.");
        assertThat(ShortcodeCapabilities.allows(till, DarajaFeature.BUSINESS_TO_POCHI)).isFalse();
        assertThat(ShortcodeCapabilities.allows(till, DarajaFeature.B2B_EXPRESS_CHECKOUT)).isFalse();
        assertThat(ShortcodeCapabilities.allows(till, DarajaFeature.PULL_TRANSACTIONS)).isTrue();
        assertThat(ShortcodeCapabilities.allows(till, DarajaFeature.TAX_REMITTANCE)).isTrue();
    }

    @Test
    void onlyATillUsesItsStoreNumberAsTheGoLiveNumber() {
        MpesaShortCode till = new MpesaShortCode();
        till.setShortcode("5123456");
        till.setStoreNumber("174300");
        till.setShortcodeType(ShortcodeType.TILL);
        MpesaShortCode paybill = new MpesaShortCode();
        paybill.setShortcode("600100");
        paybill.setStoreNumber("174300");
        paybill.setShortcodeType(ShortcodeType.PAYBILL);

        assertThat(ShortcodeCapabilities.goLiveNumber(till)).isEqualTo("174300");
        assertThat(ShortcodeCapabilities.goLiveNumber(paybill)).isEqualTo("600100");
        till.setStoreNumber(null);
        assertThat(ShortcodeCapabilities.goLiveNumber(till)).isEqualTo("5123456");
    }
}
