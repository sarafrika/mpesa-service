package com.sarafrika.apps.mpesaservice.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionStatusService;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Safaricom always gets a 200 ack, even for a body without a Result or one that is not JSON at all. */
@WebMvcTest(B2CB2BCallbackController.class)
@Import(CallbackSecurityConfig.class)
class B2CB2BCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MpesaPayoutService payoutService;
    @MockitoBean
    private TransactionStatusService statusService;

    @ParameterizedTest
    @ValueSource(strings = {"/b2c/result", "/b2b/result", "/transaction-status/result", "/account-balance/result",
            "/reversal/result"})
    void malformedResultsAreStillAcknowledged(String path) throws Exception {
        mockMvc.perform(post("/api/v1/callbacks" + path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/callbacks" + path).contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isOk());
    }
}
