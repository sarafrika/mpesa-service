package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.models.C2BValidationRule;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.C2BValidationRuleRepository;
import com.sarafrika.apps.mpesaservice.repositories.CallbackInboxRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeCapabilities;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Validation rules that never run: confirmations arrive but no validation request ever has, which means
 * Safaricom has not switched External Validation on (it is off until asked for by email). */
@Component
@Order(17)
public class ExternalValidationAttentionSource implements AttentionSource {

    static final String KIND = "EXTERNAL_VALIDATION_NOT_SEEN";
    static final Duration RECENT = Duration.ofDays(7);

    private final MpesaShortCodeRepository shortcodes;
    private final C2BValidationRuleRepository rules;
    private final CallbackInboxRepository inbox;
    private final Clock clock;

    public ExternalValidationAttentionSource(MpesaShortCodeRepository shortcodes, C2BValidationRuleRepository rules,
                                             CallbackInboxRepository inbox, Optional<Clock> clock) {
        this.shortcodes = shortcodes;
        this.rules = rules;
        this.inbox = inbox;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        Set<UUID> ruled = rules.findByActiveTrue().stream().map(C2BValidationRule::getShortcodeUuid)
                .collect(Collectors.toSet());
        if (ruled.isEmpty()) {
            return List.of();
        }
        Set<UUID> confirmed = new HashSet<>(inbox.shortcodesReceiving(CallbackKind.C2B_CONFIRMATION,
                clock.instant().minus(RECENT)));
        Set<UUID> validated = new HashSet<>(inbox.shortcodesReceiving(CallbackKind.C2B_VALIDATION, Instant.EPOCH));
        return shortcodes.findByIsActiveTrue().stream()
                .filter(code -> scope.includes(code.getUuid()))
                .filter(code -> scope.environment() == null || scope.environment() == code.getEnvironment())
                .filter(code -> ruled.contains(code.getUuid()) && confirmed.contains(code.getUuid())
                        && !validated.contains(code.getUuid()))
                .filter(code -> ShortcodeCapabilities.has(code, ShortcodeCapability.C2B_VALIDATION))
                .map(ExternalValidationAttentionSource::item)
                .toList();
    }

    private static AttentionItemDto item(MpesaShortCode code) {
        return new AttentionItemDto(KIND, AttentionItemDto.WARNING,
                "Validation rules on " + code.getBusinessName() + " · " + code.getShortcode() + " never run",
                "Payments are confirmed but Safaricom has never sent a validation request, so it has not switched "
                        + "on External Validation. Email apisupport@safaricom.co.ke to turn it on for "
                        + code.getShortcode() + ".",
                "/paybills/" + code.getUuid() + "?tab=validation", code.getUuid());
    }
}
