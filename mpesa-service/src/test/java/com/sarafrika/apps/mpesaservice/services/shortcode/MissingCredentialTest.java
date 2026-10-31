package com.sarafrika.apps.mpesaservice.services.shortcode;

import static org.assertj.core.api.Assertions.assertThat;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeOperation;
import org.junit.jupiter.api.Test;

class MissingCredentialTest {

    @Test
    void theInitiatorIsNotAskedForWhileTheRequirementIsPaused() {
        MpesaShortCode code = new MpesaShortCode();

        assertThat(ShortcodeAdminService.missingCredential(code, ShortcodeOperation.REVERSAL, false)).isNull();
        assertThat(ShortcodeAdminService.missingCredential(code, ShortcodeOperation.B2C_PAYOUT, false)).isNull();
    }

    @Test
    void theInitiatorIsAskedForWhenRequired() {
        MpesaShortCode code = new MpesaShortCode();

        assertThat(ShortcodeAdminService.missingCredential(code, ShortcodeOperation.REVERSAL, true))
                .startsWith("Add the API initiator under Credentials first");
    }

    @Test
    void thePasskeyIsStillRequiredForStkPush() {
        MpesaShortCode code = new MpesaShortCode();

        assertThat(ShortcodeAdminService.missingCredential(code, ShortcodeOperation.STK_PUSH, false))
                .startsWith("Add the passkey under Credentials first");
    }
}
