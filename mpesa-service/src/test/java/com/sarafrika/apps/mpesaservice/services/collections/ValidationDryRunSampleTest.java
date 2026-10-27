package com.sarafrika.apps.mpesaservice.services.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationDryRunRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationDryRunResult;
import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.models.C2BValidationRule;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.C2BValidationRuleRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.security.OutboundUrlGuard;
import com.sarafrika.apps.mpesaservice.utils.enums.LookupFailurePolicy;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The wizard's dry run sends no account or amount: the service builds a payment the saved rules accept. */
class ValidationDryRunSampleTest {

    private final C2BValidationRuleRepository rules = mock(C2BValidationRuleRepository.class);
    private final MpesaShortCodeRepository shortcodes = mock(MpesaShortCodeRepository.class);
    private final OutboundUrlGuard guard = new OutboundUrlGuard(java.net.InetAddress::getAllByName, true);
    private final ValidationRuleService service = new ValidationRuleService(rules, shortcodes,
            new C2BValidationService(rules, shortcodes, new AccountLookupClient(new ObjectMapper(), guard)),
            mock(AuditService.class), guard);

    @Test
    void aSampleSatisfiesTheSavedPatternAndMinimum() {
        MpesaShortCode code = code(ShortcodeType.PAYBILL);
        saved(code, "^ELM-[0-9]{6}$", "50");

        ValidationDryRunResult result = service.dryRun(code.getUuid(), new ValidationDryRunRequest(null, null, null,
                null));

        assertThat(result.accepted()).isTrue();
        assertThat(result.account()).isEqualTo("ELM-000000");
        assertThat(result.amount()).isEqualByComparingTo("50");
    }

    @Test
    void anAccountTheRequestGivesIsUsedAndAPatternWithoutASampleAsksForOne() {
        MpesaShortCode code = code(ShortcodeType.PAYBILL);
        saved(code, "^[^0-9]+$", null);

        assertThat(service.dryRun(code.getUuid(), new ValidationDryRunRequest("ABC", null, null, null)).amount())
                .isEqualByComparingTo("1");
        assertThatThrownBy(() -> service.dryRun(code.getUuid(), new ValidationDryRunRequest(null, null, null, null)))
                .isInstanceOfSatisfying(InvalidRequestException.class, e -> assertThat(e.getField()).isEqualTo("account"));
    }

    @Test
    void aTillIgnoresThePatternAndAB2cShortcodeHasNoValidation() {
        MpesaShortCode till = code(ShortcodeType.TILL);
        saved(till, "^ELM-[0-9]{6}$", null);
        MpesaShortCode b2c = code(ShortcodeType.B2C);

        assertThat(service.dryRun(till.getUuid(), new ValidationDryRunRequest(null, null, null, null)).accepted())
                .isTrue();
        assertThatThrownBy(() -> service.dryRun(b2c.getUuid(), new ValidationDryRunRequest(null, null, null, null)))
                .isInstanceOf(CapabilityNotSupportedException.class);
    }

    private void saved(MpesaShortCode code, String pattern, String min) {
        C2BValidationRule rule = new C2BValidationRule();
        rule.setShortcodeUuid(code.getUuid());
        rule.setActive(true);
        rule.setAccountPattern(pattern);
        rule.setMinAmount(min == null ? null : new BigDecimal(min));
        rule.setOnLookupFailure(LookupFailurePolicy.REJECT);
        when(rules.findByShortcodeUuid(code.getUuid())).thenReturn(Optional.of(rule));
    }

    private MpesaShortCode code(ShortcodeType type) {
        MpesaShortCode code = new MpesaShortCode();
        code.setUuid(UUID.randomUUID());
        code.setShortcode("600100");
        code.setShortcodeType(type);
        when(shortcodes.findByUuid(code.getUuid())).thenReturn(Optional.of(code));
        return code;
    }
}
