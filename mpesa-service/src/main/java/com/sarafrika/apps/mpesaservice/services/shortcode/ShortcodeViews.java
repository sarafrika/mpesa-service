package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeCredentialsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeStatus;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeSummaryDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Shortcodes as the console sees them; secrets never leave as more than their last four characters. */
final class ShortcodeViews {

    static final String MASK = "••••";

    private ShortcodeViews() {
    }

    static ShortcodeStatus status(MpesaShortCode code) {
        if (Boolean.TRUE.equals(code.getIsActive())) {
            return ShortcodeStatus.ACTIVE;
        }
        return code.getActivatedAt() == null ? ShortcodeStatus.DRAFT : ShortcodeStatus.SUSPENDED;
    }

    /** "••••Qx7d"; a value of four characters or fewer shows only the mask, a missing one null. */
    static String mask(String secret) {
        if (!StringUtils.hasLength(secret)) {
            return null;
        }
        return secret.length() <= 4 ? MASK : MASK + secret.substring(secret.length() - 4);
    }

    static LocalDateTime credentialsUpdatedAt(MpesaShortCode code) {
        return code.getCredentialsUpdatedAt() != null ? code.getCredentialsUpdatedAt() : code.getCreatedAt();
    }

    static ShortcodeCredentialsDto credentials(MpesaShortCode code) {
        return new ShortcodeCredentialsDto(mask(code.getConsumerKey()), mask(code.getConsumerSecret()),
                mask(code.getPasskey()), code.getInitiatorName(), StringUtils.hasLength(code.getInitiatorPassword()),
                StringUtils.hasLength(code.getSecurityCredential()), credentialsUpdatedAt(code));
    }

    static ShortcodeSummaryDto summary(MpesaShortCode code, ShortcodeHealthDto health,
                                       List<ShortcodeSummaryDto.ProductRef> products) {
        return new ShortcodeSummaryDto(code.getUuid(), code.getShortcode(), code.getBusinessName(),
                code.getShortcodeType(), code.getEnvironment(), status(code), Boolean.TRUE.equals(code.getIsActive()),
                credentialsUpdatedAt(code), code.getCreatedAt(), health, products);
    }

    /** stkCallbackUrl is the URL STK requests are sent with; every URL is shown with its token masked. */
    static ShortcodeDetailDto detail(MpesaShortCode code, ShortcodeHealthDto health, String stkCallbackUrl,
                                     List<ShortcodeSummaryDto.ProductRef> products) {
        return new ShortcodeDetailDto(code.getUuid(), code.getShortcode(), code.getBusinessName(),
                code.getShortcodeType(), code.getEnvironment(), status(code), Boolean.TRUE.equals(code.getIsActive()),
                code.getAccountReference(), code.getTransactionDesc(), code.getMinAmount(), code.getMaxAmount(),
                CallbackUrls.masked(stkCallbackUrl), CallbackUrls.masked(code.getConfirmationUrl()),
                CallbackUrls.masked(code.getValidationUrl()), CallbackUrls.masked(code.getB2cResultUrl()),
                CallbackUrls.masked(code.getB2cTimeoutUrl()), code.getActivatedAt(), code.getSuspendedAt(), code.getCreatedAt(),
                code.getUpdatedAt(), code.getCreatedBy(), code.getUpdatedBy(),
                credentials(code), health, products);
    }

    /** The non-secret fields an audit entry shows before and after a change. */
    static Map<String, Object> profile(MpesaShortCode code) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("shortcode", code.getShortcode());
        profile.put("shortcode_type", code.getShortcodeType());
        profile.put("business_name", code.getBusinessName());
        profile.put("environment", code.getEnvironment());
        profile.put("status", status(code));
        profile.put("min_amount", code.getMinAmount());
        profile.put("max_amount", code.getMaxAmount());
        profile.put("account_reference", code.getAccountReference());
        profile.put("transaction_desc", code.getTransactionDesc());
        profile.put("callback_url", code.getCallbackUrl());
        profile.put("b2c_result_url", code.getB2cResultUrl());
        profile.put("b2c_timeout_url", code.getB2cTimeoutUrl());
        profile.put("initiator_name", code.getInitiatorName());
        return profile;
    }
}
