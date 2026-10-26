package com.sarafrika.apps.mpesaservice.controllers;

import static com.sarafrika.apps.mpesaservice.controllers.MachineApiTokens.product;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkPushResponse;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.products.ProductGrantGuard;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** mpesa.grants.enforce=false: any authenticated client may use any shortcode, as before grants existed. */
@WebMvcTest(value = {MpesaPaymentController.class, MpesaPayoutController.class},
        properties = "mpesa.grants.enforce=false")
@Import({CallbackSecurityConfig.class, ProductGrantGuard.class})
class MachineApiGrantsDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MpesaDarajaService darajaService;
    @MockitoBean
    private MpesaIncomingPaymentService incomingPaymentService;
    @MockitoBean
    private MpesaPayoutService payoutService;
    @MockitoBean
    private ClientShortcodeGrantRepository grants;

    @Test
    void anUngrantedProductMayPushPayOutAndLookUp() throws Exception {
        UUID paybill = UUID.randomUUID();
        when(darajaService.initiateSTKPush(eq(paybill), any(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.success(new StkPushResponse("m", "ws_CO_1", "0", "ok", "ok"), 200));
        MpesaOutgoingPayment payout = new MpesaOutgoingPayment();
        payout.setUuid(UUID.randomUUID());
        payout.setStatus(OutgoingPaymentStatus.PENDING);
        when(payoutService.initiate(eq("stranger"), any())).thenReturn(payout);
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setShortcodeUuid(paybill);
        payment.setAmount(BigDecimal.TEN);
        when(incomingPaymentService.findByAccountReference("INV-1")).thenReturn(List.of(payment));

        mockMvc.perform(post("/api/v1/mpesa/stk-push").with(product("stranger")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shortcode_uuid":"%s","phone_number":"254708374149","amount":10}""".formatted(paybill)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/mpesa/b2c").with(product("stranger")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shortcode_uuid":"%s","phone_number":"254712345678","amount":1500,
                                 "client_reference":"WD-1"}""".formatted(paybill)))
                .andExpect(status().isAccepted());
        mockMvc.perform(get("/api/v1/mpesa/payments").param("account_reference", "INV-1").with(product("stranger")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verifyNoInteractions(grants);
    }
}
