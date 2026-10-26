package com.sarafrika.apps.mpesaservice.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MpesaPayoutController.class)
@AutoConfigureMockMvc(addFilters = false)
class MpesaPayoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MpesaPayoutService payoutService;

    @Test
    void anAcceptedPayoutAnswers202WithThePayout() throws Exception {
        MpesaOutgoingPayment payment = new MpesaOutgoingPayment();
        payment.setUuid(UUID.randomUUID());
        payment.setClientReference("WD-1");
        payment.setAmount(new BigDecimal("1500"));
        payment.setStatus(OutgoingPaymentStatus.PENDING);
        when(payoutService.initiate(eq("anonymous"), any())).thenReturn(payment);

        mockMvc.perform(post("/api/v1/mpesa/b2c").contentType(MediaType.APPLICATION_JSON).content("""
                        {"shortcode_uuid":"%s","phone_number":"254712345678","amount":1500,"client_reference":"WD-1"}"""
                        .formatted(UUID.randomUUID())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.client_reference").value("WD-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void aRequestWithoutAReferenceIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/mpesa/b2c").contentType(MediaType.APPLICATION_JSON).content("""
                        {"shortcode_uuid":"%s","phone_number":"254712345678","amount":1500}"""
                        .formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void payoutsBelongToTheCallingKeycloakClient() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("azp", "elimika-backend", "sub", "service-account"));

        assertThat(MpesaPayoutController.clientId(new JwtAuthenticationToken(jwt))).isEqualTo("elimika-backend");
        assertThat(MpesaPayoutController.clientId(null)).isEqualTo("anonymous");
    }
}
