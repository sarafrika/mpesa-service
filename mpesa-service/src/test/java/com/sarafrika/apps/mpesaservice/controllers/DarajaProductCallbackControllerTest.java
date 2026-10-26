package com.sarafrika.apps.mpesaservice.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.services.daraja.products.B2bCheckoutService;
import com.sarafrika.apps.mpesaservice.services.daraja.products.ProductPayoutService;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Safaricom always gets a 200 ack; a handler failure is acked with an error code for the inbox. */
@WebMvcTest(DarajaProductCallbackController.class)
@Import(CallbackSecurityConfig.class)
class DarajaProductCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductPayoutService payouts;
    @MockitoBean
    private B2bCheckoutService checkouts;

    @ParameterizedTest
    @ValueSource(strings = {"/tax-remittance/result", "/tax-remittance/timeout", "/pochi/result", "/pochi/timeout",
            "/b2b-checkout/result"})
    void everyCallbackIsAcknowledged(String path) throws Exception {
        mockMvc.perform(post("/api/v1/callbacks" + path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    // The Pochi failure sample on developer.safaricom.co.ke/apis/BusinessToPochi.
    @Test
    void aPochiResultReachesItsPayment() throws Exception {
        mockMvc.perform(post("/api/v1/callbacks/pochi/result").contentType(MediaType.APPLICATION_JSON).content("""
                        {"Result":{"ResultType":0,"ResultCode":2001,"ResultDesc":"The initiator information is invalid.",
                        "OriginatorConversationID":"53e3-4aa8-9fe0-8fb5e4092cdd3544366",
                        "ConversationID":"AG_20240707_201062f6f6f5804f7a33","TransactionID":"SG722NMVXQ"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("00000000"));
        verify(payouts).applyResult(eq(DarajaFeature.BUSINESS_TO_POCHI), any(), anyString());
    }

    @Test
    void aFailingHandlerIsAckedWithAnError() throws Exception {
        when(checkouts.applyResult(any(), anyString())).thenThrow(new IllegalStateException("database down"));
        mockMvc.perform(post("/api/v1/callbacks/b2b-checkout/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultCode\":\"0\",\"requestId\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("00000001"));
    }
}
