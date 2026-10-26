package com.sarafrika.apps.mpesaservice.services.collections;

import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.UpdateValidationRulesRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationDryRunRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationDryRunResult;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationRulesDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.C2BValidationRule;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.C2BValidationRuleRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.LookupFailurePolicy;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Reads, replaces (audited) and dry-runs a shortcode's C2B validation rules for the console. */
@Service
@RequiredArgsConstructor
public class ValidationRuleService {

    static final String AUDIT_ACTION = "VALIDATION_RULES_UPDATED";

    private final C2BValidationRuleRepository ruleRepository;
    private final MpesaShortCodeRepository shortCodeRepository;
    private final C2BValidationService validationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public ValidationRulesDto get(UUID shortcodeUuid) {
        shortcode(shortcodeUuid);
        return ruleRepository.findByShortcodeUuid(shortcodeUuid).map(ValidationRuleService::toDto)
                .orElseGet(() -> defaults(shortcodeUuid));
    }

    @Transactional
    public ValidationRulesDto update(UUID shortcodeUuid, UpdateValidationRulesRequest request) {
        shortcode(shortcodeUuid);
        check(request);
        C2BValidationRule rule = ruleRepository.findByShortcodeUuid(shortcodeUuid).orElseGet(() -> {
            C2BValidationRule created = new C2BValidationRule();
            created.setShortcodeUuid(shortcodeUuid);
            created.setCreatedBy(CurrentActor.name());
            return created;
        });
        ValidationRulesDto before = rule.getId() == null ? null : toDto(rule);
        rule.setActive(request.active());
        rule.setAccountPattern(blankToNull(request.accountPattern()));
        rule.setMinAmount(request.minAmount());
        rule.setMaxAmount(request.maxAmount());
        rule.setLookupUrl(blankToNull(request.lookupUrl()));
        rule.setLookupTimeoutMs(request.lookupTimeoutMs() == null ? C2BValidationRule.DEFAULT_LOOKUP_TIMEOUT_MS
                : request.lookupTimeoutMs());
        rule.setOnLookupFailure(request.onLookupFailure());
        rule.setUpdatedBy(CurrentActor.name());
        ValidationRulesDto after = toDto(ruleRepository.saveAndFlush(rule));
        auditService.record(AUDIT_ACTION, "SHORTCODE", shortcodeUuid.toString(), before, after);
        return after;
    }

    /** Runs a pretend payment through the saved rules (or the draft in the request), lookup included. */
    @Transactional(readOnly = true)
    public ValidationDryRunResult dryRun(UUID shortcodeUuid, ValidationDryRunRequest request) {
        MpesaShortCode shortcode = shortcode(shortcodeUuid);
        C2BValidationService.RuleSet rules;
        if (request.rules() != null) {
            check(request.rules());
            UpdateValidationRulesRequest draft = request.rules();
            rules = new C2BValidationService.RuleSet(draft.active(), blankToNull(draft.accountPattern()),
                    draft.minAmount(), draft.maxAmount(), blankToNull(draft.lookupUrl()),
                    draft.lookupTimeoutMs() == null ? C2BValidationRule.DEFAULT_LOOKUP_TIMEOUT_MS : draft.lookupTimeoutMs(),
                    draft.onLookupFailure());
        } else {
            rules = ruleRepository.findByShortcodeUuid(shortcodeUuid).map(C2BValidationService.RuleSet::of).orElse(null);
        }
        ValidationDecision decision = validationService.evaluate(rules, shortcode.getShortcode(), request.account(),
                request.amount(), request.msisdn(), "DRYRUN");
        return new ValidationDryRunResult(decision.accepted(), decision.resultCode(), decision.reason());
    }

    private void check(UpdateValidationRulesRequest request) {
        if (StringUtils.hasText(request.accountPattern())) {
            String error = C2BValidationService.patternError(request.accountPattern());
            if (error != null) {
                throw new InvalidRequestException("account_pattern", error);
            }
        }
        if (request.minAmount() != null && request.maxAmount() != null
                && request.minAmount().compareTo(request.maxAmount()) > 0) {
            throw new InvalidRequestException("max_amount", "must not be below the minimum amount");
        }
    }

    private MpesaShortCode shortcode(UUID uuid) {
        return shortCodeRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Shortcode not found"));
    }

    private static ValidationRulesDto defaults(UUID shortcodeUuid) {
        return new ValidationRulesDto(shortcodeUuid, false, false, null, null, null, null,
                C2BValidationRule.DEFAULT_LOOKUP_TIMEOUT_MS, LookupFailurePolicy.REJECT.name(), null, null);
    }

    static ValidationRulesDto toDto(C2BValidationRule rule) {
        return new ValidationRulesDto(rule.getShortcodeUuid(), true, Boolean.TRUE.equals(rule.getActive()),
                rule.getAccountPattern(), rule.getMinAmount(), rule.getMaxAmount(), rule.getLookupUrl(),
                rule.getLookupTimeoutMs(), rule.getOnLookupFailure().name(), Times.offset(rule.getUpdatedAt()),
                rule.getUpdatedBy());
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
