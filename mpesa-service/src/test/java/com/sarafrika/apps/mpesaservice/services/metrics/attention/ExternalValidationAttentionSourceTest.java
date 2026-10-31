package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.models.C2BValidationRule;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.C2BValidationRuleRepository;
import com.sarafrika.apps.mpesaservice.repositories.CallbackInboxRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Confirmations without a single validation request mean Safaricom never switched External Validation on. */
class ExternalValidationAttentionSourceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T09:00:00Z");

    private final MpesaShortCodeRepository shortcodes = mock(MpesaShortCodeRepository.class);
    private final C2BValidationRuleRepository rules = mock(C2BValidationRuleRepository.class);
    private final CallbackInboxRepository inbox = mock(CallbackInboxRepository.class);
    private final ExternalValidationAttentionSource source = new ExternalValidationAttentionSource(shortcodes, rules,
            inbox, Optional.of(Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    void onlyARuledConfirmedNeverValidatedShortcodeWithC2bOnIsListed() {
        MpesaShortCode unseen = shortcode(EnumSet.of(ShortcodeOperation.C2B));
        MpesaShortCode validated = shortcode(EnumSet.of(ShortcodeOperation.C2B));
        MpesaShortCode quiet = shortcode(EnumSet.of(ShortcodeOperation.C2B));
        MpesaShortCode noRule = shortcode(EnumSet.of(ShortcodeOperation.C2B));
        MpesaShortCode c2bOff = shortcode(EnumSet.of(ShortcodeOperation.STK_PUSH));
        when(shortcodes.findByIsActiveTrue()).thenReturn(List.of(unseen, validated, quiet, noRule, c2bOff));
        when(rules.findByActiveTrue()).thenReturn(List.of(rule(unseen), rule(validated), rule(quiet), rule(c2bOff)));
        when(inbox.shortcodesReceiving(eq(CallbackKind.C2B_CONFIRMATION), eq(NOW.minus(
                ExternalValidationAttentionSource.RECENT)))).thenReturn(List.of(unseen.getUuid(),
                validated.getUuid(), noRule.getUuid(), c2bOff.getUuid()));
        when(inbox.shortcodesReceiving(eq(CallbackKind.C2B_VALIDATION), any()))
                .thenReturn(List.of(validated.getUuid()));

        List<AttentionItemDto> items = source.currentItems(AttentionScope.ALL);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(ExternalValidationAttentionSource.KIND);
            assertThat(item.shortcodeUuid()).isEqualTo(unseen.getUuid());
            assertThat(item.description()).contains("apisupport@safaricom.co.ke");
        });
        assertThat(source.currentItems(new AttentionScope(null, Environment.SANDBOX))).isEmpty();
        assertThat(source.currentItems(new AttentionScope(validated.getUuid(), null))).isEmpty();
    }

    private static MpesaShortCode shortcode(EnumSet<ShortcodeOperation> operations) {
        MpesaShortCode code = new MpesaShortCode();
        code.setUuid(UUID.randomUUID());
        code.setShortcode("600100");
        code.setBusinessName("Fees");
        code.setShortcodeType(ShortcodeType.PAYBILL);
        code.setEnvironment(Environment.PRODUCTION);
        code.setOperations(operations);
        return code;
    }

    private static C2BValidationRule rule(MpesaShortCode code) {
        C2BValidationRule rule = new C2BValidationRule();
        rule.setShortcodeUuid(code.getUuid());
        return rule;
    }
}
