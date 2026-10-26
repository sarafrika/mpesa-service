package com.sarafrika.apps.mpesaservice.services.shortcode;

import static org.assertj.core.api.Assertions.assertThat;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import org.junit.jupiter.api.Test;

class CallbackUrlsTest {

    private static final String BASE = "https://pay.example.test/api/v1/callbacks";
    private static final String TOKEN = "0".repeat(60) + "9f3e";

    private final CallbackUrls urls = new CallbackUrls(BASE + "/");

    @Test
    void eachShortcodeGetsItsOwnTokenPath() {
        MpesaShortCode code = code();
        assertThat(urls.url(code, "/b2b/result")).isEqualTo(BASE + "/" + TOKEN + "/b2b/result");
        assertThat(urls.forShortcode(code).c2bConfirmationUrl()).isEqualTo(BASE + "/" + TOKEN + "/c2b/confirmation");
        assertThat(urls.url(new MpesaShortCode(), "/b2b/result")).isEqualTo(BASE + "/b2b/result");
    }

    @Test
    void theSharedDefaultBecomesTheTokenUrlButAHandSetUrlIsKept() {
        MpesaShortCode code = code();
        code.setCallbackUrl(BASE + "/stk/callback");
        assertThat(urls.stk(code)).isEqualTo(BASE + "/" + TOKEN + "/stk/callback");
        code.setCallbackUrl("https://elsewhere.example/stk");
        assertThat(urls.stk(code)).isEqualTo("https://elsewhere.example/stk");
        assertThat(urls.b2cResult(code)).isEqualTo(BASE + "/" + TOKEN + "/b2c/result");
    }

    @Test
    void screensSeeOnlyTheTokensLastFourCharacters() {
        assertThat(CallbackUrls.masked(BASE + "/" + TOKEN + "/stk/callback")).isEqualTo(BASE + "/••••9f3e/stk/callback");
        assertThat(CallbackUrls.masked(BASE + "/stk/callback")).isEqualTo(BASE + "/stk/callback");
        assertThat(urls.template().stkCallbackUrl()).isEqualTo(BASE + "/{callback-token}/stk/callback");
    }

    private static MpesaShortCode code() {
        MpesaShortCode code = new MpesaShortCode();
        code.setCallbackToken(TOKEN);
        return code;
    }
}
