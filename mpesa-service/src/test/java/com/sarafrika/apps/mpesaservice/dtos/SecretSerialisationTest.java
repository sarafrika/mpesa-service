package com.sarafrika.apps.mpesaservice.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Credentials are accepted on requests but never written into a response. */
class SecretSerialisationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shortcodeCredentialsAreWriteOnly() throws Exception {
        MpesaShortCodeDto dto = MpesaShortCodeDto.forCreation("174379", ShortcodeType.PAYBILL, "Acme",
                "the-consumer-key", "the-consumer-secret", "the-passkey", "https://example.test/cb", null, null,
                BigDecimal.ONE, BigDecimal.TEN, Environment.SANDBOX, null, null);

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"shortcode\":\"174379\"")
                .doesNotContain("consumer_key", "consumer_secret", "passkey",
                        "the-consumer-key", "the-consumer-secret", "the-passkey");
    }

    @Test
    void shortcodeCredentialsCanStillBeSent() throws Exception {
        MpesaShortCodeDto dto = mapper.readValue("""
                {"shortcode":"174379","consumer_key":"k","consumer_secret":"s","passkey":"p"}""",
                MpesaShortCodeDto.class);

        assertThat(dto.consumerKey()).isEqualTo("k");
        assertThat(dto.consumerSecret()).isEqualTo("s");
        assertThat(dto.passkey()).isEqualTo("p");
    }

    @Test
    void payoutResponsesCarryNoShortcodeCredentials() throws Exception {
        MpesaOutgoingPayment payment = new MpesaOutgoingPayment();
        payment.setUuid(UUID.randomUUID());
        payment.setAmount(new BigDecimal("1500"));
        payment.setStatus(OutgoingPaymentStatus.PENDING);

        String json = mapper.writeValueAsString(MpesaPayoutDto.from(payment)).toLowerCase();

        assertThat(json).doesNotContain("secret", "password", "passkey", "credential", "consumer_key");
    }
}
