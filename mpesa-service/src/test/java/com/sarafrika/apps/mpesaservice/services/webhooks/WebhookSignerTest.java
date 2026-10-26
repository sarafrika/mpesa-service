package com.sarafrika.apps.mpesaservice.services.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String BODY = "{\"id\":\"evt_1\",\"type\":\"c2b.confirmed\"}";

    @Test
    void signsTimestampDotBodyWithHmacSha256() {
        // printf '%s' '1767225600.<body>' | openssl dgst -sha256 -hmac whsec_test_secret
        assertThat(WebhookSigner.signatureHeader(SECRET, 1767225600L, BODY)).isEqualTo(
                "t=1767225600,v1=5f26d229a707fd719b36ee4cbdcefbe50eb2b4878b192c1b333cef30129cbf78");
    }

    @Test
    void verifiesItsOwnSignatureAndRejectsTampering() {
        String header = WebhookSigner.signatureHeader(SECRET, 1767225600L, BODY);

        assertThat(WebhookSigner.verify(SECRET, header, BODY)).isTrue();
        assertThat(WebhookSigner.verify(SECRET, header, BODY.replace("c2b", "b2c"))).isFalse();
        assertThat(WebhookSigner.verify("whsec_other", header, BODY)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, header.replace("t=1767225600", "t=1767225601"), BODY)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, null, BODY)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, "v1=abc", BODY)).isFalse();
    }

    @Test
    void newSecretsAreRandomAndShownOnlyAsAHint() {
        String one = WebhookSigner.newSecret();
        String two = WebhookSigner.newSecret();

        assertThat(one).startsWith("whsec_").hasSize(6 + 43).isNotEqualTo(two);
        assertThat(WebhookSigner.hint(one)).isEqualTo("••••" + one.substring(one.length() - 4));
        assertThat(WebhookSigner.hint(null)).isEqualTo("••••");
    }
}
