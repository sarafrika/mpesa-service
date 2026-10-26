package com.sarafrika.apps.mpesaservice.services.collections;

import com.sarafrika.apps.mpesaservice.dtos.C2BValidationRequest;
import com.sarafrika.apps.mpesaservice.models.C2BValidationRule;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.C2BValidationRuleRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.LookupFailurePolicy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Answers Safaricom's C2B validation request from the shortcode's rules; no rule (or an inactive one) accepts. */
@Service
@RequiredArgsConstructor
@Slf4j
public class C2BValidationService {

    private final C2BValidationRuleRepository ruleRepository;
    private final MpesaShortCodeRepository shortCodeRepository;
    private final AccountLookupClient lookupClient;
    private final Map<String, Pattern> patterns = new ConcurrentHashMap<>();

    /** The rules one evaluation applies: saved ones, or a draft being dry-run from the console. */
    public record RuleSet(boolean active, String accountPattern, BigDecimal minAmount, BigDecimal maxAmount,
                          String lookupUrl, int lookupTimeoutMs, LookupFailurePolicy onLookupFailure) {

        public static RuleSet of(C2BValidationRule rule) {
            return new RuleSet(Boolean.TRUE.equals(rule.getActive()), rule.getAccountPattern(), rule.getMinAmount(),
                    rule.getMaxAmount(), rule.getLookupUrl(),
                    rule.getLookupTimeoutMs() == null ? C2BValidationRule.DEFAULT_LOOKUP_TIMEOUT_MS
                            : rule.getLookupTimeoutMs(),
                    rule.getOnLookupFailure() == null ? LookupFailurePolicy.REJECT : rule.getOnLookupFailure());
        }
    }

    @Transactional(readOnly = true)
    public ValidationDecision validate(C2BValidationRequest request) {
        Optional<MpesaShortCode> shortcode = Optional.ofNullable(request.businessShortCode())
                .map(String::trim)
                .flatMap(shortCodeRepository::findByShortcode);
        if (shortcode.isEmpty()) {
            return ValidationDecision.reject(C2BResultCodes.INVALID_SHORTCODE,
                    "Shortcode " + request.businessShortCode() + " is not configured here");
        }
        RuleSet rules = ruleRepository.findByShortcodeUuid(shortcode.get().getUuid()).map(RuleSet::of).orElse(null);
        return evaluate(rules, shortcode.get().getShortcode(), request.billRefNumber(), request.transAmount(),
                request.msisdn(), request.transId());
    }

    public ValidationDecision evaluate(RuleSet rules, String shortcode, String account, BigDecimal amount,
                                       String msisdn, String transId) {
        if (rules == null || !rules.active()) {
            return ValidationDecision.accept("No active validation rules; every payment is accepted");
        }
        if (msisdn != null && msisdn.isBlank()) {
            return ValidationDecision.reject(C2BResultCodes.INVALID_MSISDN, "The payer's phone number is missing");
        }
        if (amount == null || amount.signum() <= 0) {
            return ValidationDecision.reject(C2BResultCodes.INVALID_AMOUNT, "The amount must be above zero");
        }
        if (rules.minAmount() != null && amount.compareTo(rules.minAmount()) < 0) {
            return ValidationDecision.reject(C2BResultCodes.INVALID_AMOUNT,
                    "Amount is below the minimum of KES " + rules.minAmount().toPlainString());
        }
        if (rules.maxAmount() != null && amount.compareTo(rules.maxAmount()) > 0) {
            return ValidationDecision.reject(C2BResultCodes.INVALID_AMOUNT,
                    "Amount is above the maximum of KES " + rules.maxAmount().toPlainString());
        }
        String reference = account == null ? "" : account.trim();
        if (StringUtils.hasText(rules.accountPattern())) {
            ValidationDecision mismatch = checkAccount(rules.accountPattern(), reference, shortcode);
            if (mismatch != null) {
                return mismatch;
            }
        }
        if (StringUtils.hasText(rules.lookupUrl())) {
            return lookup(rules, shortcode, reference, amount, msisdn, transId);
        }
        return ValidationDecision.accept("Passed the shortcode's validation rules");
    }

    private ValidationDecision lookup(RuleSet rules, String shortcode, String account, BigDecimal amount,
                                      String msisdn, String transId) {
        AccountLookupClient.LookupResult result = lookupClient.lookup(rules.lookupUrl(), rules.lookupTimeoutMs(),
                new AccountLookupClient.LookupRequest(shortcode, account, amount, msisdn, transId));
        if (!result.answered()) {
            log.warn("C2B account lookup for {} failed ({}); policy {}", shortcode, result.reason(),
                    rules.onLookupFailure());
            return rules.onLookupFailure() == LookupFailurePolicy.ACCEPT
                    ? ValidationDecision.accept(result.reason() + "; accepted by policy")
                    : ValidationDecision.reject(C2BResultCodes.OTHER_ERROR, result.reason() + "; rejected by policy");
        }
        if (result.valid()) {
            return ValidationDecision.accept(result.reason() != null ? result.reason() : "The product confirmed the account");
        }
        // A product may name a more precise rejection (e.g. C2B00011); anything unknown becomes invalid account.
        String code = result.resultCode() != null && C2BResultCodes.REJECTIONS.contains(result.resultCode())
                ? result.resultCode()
                : C2BResultCodes.INVALID_ACCOUNT;
        return ValidationDecision.reject(code, result.reason() != null ? result.reason() : "The product does not know this account");
    }

    private ValidationDecision checkAccount(String regex, String reference, String shortcode) {
        try {
            if (BoundedRegex.matches(pattern(regex), reference)) {
                return null;
            }
            return ValidationDecision.reject(C2BResultCodes.INVALID_ACCOUNT,
                    "Account '" + reference + "' does not match " + regex);
        } catch (PatternSyntaxException e) {
            log.warn("Shortcode {} has an account pattern that does not compile; rejecting", shortcode);
            return ValidationDecision.reject(C2BResultCodes.OTHER_ERROR, "The account pattern is not valid");
        } catch (BoundedRegex.TooSlowException e) {
            log.warn("Shortcode {}: the account pattern ran out of steps on '{}'; rejecting", shortcode, reference);
            return ValidationDecision.reject(C2BResultCodes.OTHER_ERROR, e.getMessage());
        }
    }

    private Pattern pattern(String regex) {
        if (patterns.size() > 500) {
            patterns.clear();
        }
        return patterns.computeIfAbsent(regex, Pattern::compile);
    }

    /** Null when the pattern is short enough, compiles and evaluates quickly, otherwise why not. */
    public static String patternError(String regex) {
        return BoundedRegex.problem(regex);
    }
}
