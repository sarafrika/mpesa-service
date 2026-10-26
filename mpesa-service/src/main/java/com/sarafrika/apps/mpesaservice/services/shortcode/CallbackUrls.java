package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeCallbackUrlsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The callback URLs this service registers with Safaricom, from mpesa.callback.* (see application.yaml). */
@Component
public class CallbackUrls {

    private final ShortcodeCallbackUrlsDto urls;

    public CallbackUrls(
            @Value("${mpesa.callback.stk.url:${mpesa.callback.base-url:}/stk/callback}") String stk,
            @Value("${mpesa.callback.c2b.confirmation-url:${mpesa.callback.base-url:}/c2b/confirmation}") String confirmation,
            @Value("${mpesa.callback.c2b.validation-url:${mpesa.callback.base-url:}/c2b/validation}") String validation,
            @Value("${mpesa.callback.b2c.result-url:${mpesa.callback.base-url:}/b2c/result}") String b2cResult,
            @Value("${mpesa.callback.b2c.timeout-url:${mpesa.callback.base-url:}/b2c/timeout}") String b2cTimeout) {
        this.urls = new ShortcodeCallbackUrlsDto(stk, confirmation, validation, b2cResult, b2cTimeout);
    }

    public ShortcodeCallbackUrlsDto all() {
        return urls;
    }
}
