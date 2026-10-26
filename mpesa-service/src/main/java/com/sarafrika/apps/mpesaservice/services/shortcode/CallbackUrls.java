package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeCallbackUrlsDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The callback URLs this service gives Safaricom, from mpesa.callback.* (see application.yaml). Each shortcode
 * gets its own under {@code <base-url>/<callback token>/...}; the older shared paths still accept callbacks.
 */
@Component
public class CallbackUrls {

    public static final String STK = "/stk/callback";
    public static final String C2B_CONFIRMATION = "/c2b/confirmation";
    public static final String C2B_VALIDATION = "/c2b/validation";
    public static final String B2C_RESULT = "/b2c/result";
    public static final String B2C_TIMEOUT = "/b2c/timeout";

    private static final Pattern TOKEN_SEGMENT = Pattern.compile("/[0-9a-f]{60}([0-9a-f]{4})/");

    private final String base;
    private final ShortcodeCallbackUrlsDto shared;

    /** Every URL derived from one base URL, as application.yaml does by default. */
    public CallbackUrls(String base) {
        this(base, base.replaceAll("/+$", "") + STK, base.replaceAll("/+$", "") + C2B_CONFIRMATION,
                base.replaceAll("/+$", "") + C2B_VALIDATION, base.replaceAll("/+$", "") + B2C_RESULT,
                base.replaceAll("/+$", "") + B2C_TIMEOUT);
    }

    @Autowired
    public CallbackUrls(
            @Value("${mpesa.callback.base-url:}") String base,
            @Value("${mpesa.callback.stk.url:${mpesa.callback.base-url:}/stk/callback}") String stk,
            @Value("${mpesa.callback.c2b.confirmation-url:${mpesa.callback.base-url:}/c2b/confirmation}") String confirmation,
            @Value("${mpesa.callback.c2b.validation-url:${mpesa.callback.base-url:}/c2b/validation}") String validation,
            @Value("${mpesa.callback.b2c.result-url:${mpesa.callback.base-url:}/b2c/result}") String b2cResult,
            @Value("${mpesa.callback.b2c.timeout-url:${mpesa.callback.base-url:}/b2c/timeout}") String b2cTimeout) {
        this.base = base == null ? "" : base.replaceAll("/+$", "");
        this.shared = new ShortcodeCallbackUrlsDto(stk, confirmation, validation, b2cResult, b2cTimeout);
    }

    /** The shared paths every shortcode used before callback tokens; they keep working. */
    public ShortcodeCallbackUrlsDto all() {
        return shared;
    }

    /** This shortcode's own URLs; one not saved yet (no token) gets the shared ones. */
    public ShortcodeCallbackUrlsDto forShortcode(MpesaShortCode code) {
        return new ShortcodeCallbackUrlsDto(url(code, STK), url(code, C2B_CONFIRMATION), url(code, C2B_VALIDATION),
                url(code, B2C_RESULT), url(code, B2C_TIMEOUT));
    }

    /** {@code <base-url>/<token><path>} for a path under /api/v1/callbacks such as "/b2b/result". */
    public String url(MpesaShortCode code, String path) {
        if (code == null || !StringUtils.hasText(code.getCallbackToken())) {
            return base + path;
        }
        return base + "/" + code.getCallbackToken() + path;
    }

    /** The STK CallBackURL: a URL an admin set by hand is kept, the shared default becomes the token URL. */
    public String stk(MpesaShortCode code) {
        return customOr(code.getCallbackUrl(), shared.stkCallbackUrl(), code, STK);
    }

    public String b2cResult(MpesaShortCode code) {
        return customOr(code.getB2cResultUrl(), shared.b2cResultUrl(), code, B2C_RESULT);
    }

    public String b2cTimeout(MpesaShortCode code) {
        return customOr(code.getB2cTimeoutUrl(), shared.b2cTimeoutUrl(), code, B2C_TIMEOUT);
    }

    /** The URL with its callback token shown as ••••last4, for screens and audit entries. */
    public static String masked(String url) {
        return url == null ? null : TOKEN_SEGMENT.matcher(url).replaceAll(m -> "/••••" + m.group(1) + "/");
    }

    /** The shared URLs with a placeholder where each shortcode's token goes. */
    public ShortcodeCallbackUrlsDto template() {
        String token = "/{callback-token}";
        return new ShortcodeCallbackUrlsDto(base + token + STK, base + token + C2B_CONFIRMATION,
                base + token + C2B_VALIDATION, base + token + B2C_RESULT, base + token + B2C_TIMEOUT);
    }

    private String customOr(String stored, String sharedDefault, MpesaShortCode code, String path) {
        if (!StringUtils.hasText(stored) || stored.trim().equals(sharedDefault) || stored.trim().equals(base + path)) {
            return url(code, path);
        }
        return stored.trim();
    }
}
