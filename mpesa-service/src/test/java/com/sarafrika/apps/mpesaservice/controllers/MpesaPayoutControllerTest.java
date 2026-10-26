package com.sarafrika.apps.mpesaservice.controllers;

import static com.sarafrika.apps.mpesaservice.controllers.MachineApiTokens.console;
import static com.sarafrika.apps.mpesaservice.controllers.MachineApiTokens.grant;
import static com.sarafrika.apps.mpesaservice.controllers.MachineApiTokens.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.PayoutFloatDto;
import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.products.ProductGrantGuard;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MpesaPayoutController.class)
@Import({CallbackSecurityConfig.class, ProductGrantGuard.class})
class MpesaPayoutControllerTest {

    private static final UUID PAYBILL = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MpesaPayoutService payoutService;
    @MockitoBean
    private ClientShortcodeGrantRepository grants;

    @BeforeEach
    void elimikaMayPayOutFromOnePaybill() {
        ClientShortcodeGrant elimika = grant("elimika-backend", PAYBILL, GrantOperation.B2C_PAYOUT);
        when(grants.findByClientIdAndShortcodeUuid(anyString(), any())).thenReturn(Optional.empty());
        when(grants.findByClientIdAndShortcodeUuid("elimika-backend", PAYBILL)).thenReturn(Optional.of(elimika));
        when(grants.findByClientIdAndActiveTrue(anyString())).thenReturn(List.of());
        when(grants.findByClientIdAndActiveTrue("elimika-backend")).thenReturn(List.of(elimika));
    }

    @Test
    void anAcceptedPayoutAnswers202WithThePayout() throws Exception {
        MpesaOutgoingPayment payment = new MpesaOutgoingPayment();
        payment.setUuid(UUID.randomUUID());
        payment.setClientReference("WD-1");
        payment.setAmount(new BigDecimal("1500"));
        payment.setStatus(OutgoingPaymentStatus.PENDING);
        when(payoutService.initiate(eq("elimika-backend"), any())).thenReturn(payment);

        mockMvc.perform(post("/api/v1/mpesa/b2c").with(product("elimika-backend"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PAYBILL, "WD-1")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.client_reference").value("WD-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void aPayoutFromAPaybillThatWasNotGrantedIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/mpesa/b2c").with(product("elimika-backend"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID(), "WD-2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.errorCode").value("GRANT_REQUIRED"));
        mockMvc.perform(post("/api/v1/mpesa/b2c").with(product("shop-api"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PAYBILL, "WD-3")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/mpesa/b2c").with(console("mpesa-finance"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PAYBILL, "WD-4")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(payoutService);
    }

    @Test
    void aRequestWithoutAReferenceIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/mpesa/b2c").with(product("elimika-backend"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"shortcode_uuid":"%s","phone_number":"254712345678","amount":1500}"""
                                .formatted(PAYBILL)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theFloatNeedsAPayoutOrLookupGrantUnlessTheCallerUsesTheConsole() throws Exception {
        when(payoutService.latestFloat(any())).thenAnswer(call ->
                new PayoutFloatDto(call.getArgument(0), new BigDecimal("98500.00"), null, null));

        mockMvc.perform(get("/api/v1/mpesa/shortcodes/{uuid}/float", PAYBILL).with(product("elimika-backend")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/mpesa/shortcodes/{uuid}/float", UUID.randomUUID())
                        .with(product("elimika-backend")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/mpesa/shortcodes/{uuid}/float", UUID.randomUUID()).with(console("mpesa-viewer")))
                .andExpect(status().isOk());
    }

    @Test
    void payoutReadsStayScopedToTheCallingClient() throws Exception {
        when(payoutService.findForClient(eq("shop-api"), any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/mpesa/payouts/{uuid}", UUID.randomUUID()).with(product("shop-api")))
                .andExpect(status().isNotFound());
    }

    @Test
    void payoutsBelongToTheCallingKeycloakClient() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("azp", "elimika-backend", "sub", "service-account"));

        assertThat(MpesaPayoutController.clientId(new JwtAuthenticationToken(jwt))).isEqualTo("elimika-backend");
        assertThat(MpesaPayoutController.clientId(null)).isEqualTo("anonymous");
    }

    private static String body(UUID shortcode, String reference) {
        return """
                {"shortcode_uuid":"%s","phone_number":"254712345678","amount":1500,"client_reference":"%s"}"""
                .formatted(shortcode, reference);
    }
}
