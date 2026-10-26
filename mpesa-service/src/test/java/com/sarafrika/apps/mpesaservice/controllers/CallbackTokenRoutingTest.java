package com.sarafrika.apps.mpesaservice.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.collections.C2BConfirmationService;
import com.sarafrika.apps.mpesaservice.services.collections.C2BValidationService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A shortcode's own callback URL reaches the same handler; an unknown token or IP is refused. */
@WebMvcTest(controllers = C2BCallbackController.class, properties = {
        "mpesa.callback.ip-whitelist.enabled=true", "mpesa.callback.allowed-ips=127.0.0.1"})
@Import(CallbackSecurityConfig.class)
class CallbackTokenRoutingTest {

    private static final String TOKEN = "a".repeat(60) + "1b2c";
    private static final String BODY = """
            {"TransactionType":"Pay Bill","TransID":"TJQ4K2L9XA","TransTime":"20261026143208","TransAmount":"4500.00",
             "BusinessShortCode":"600100","BillRefNumber":"ELM-004512","MSISDN":"254712345612"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private C2BValidationService validationService;
    @MockitoBean
    private C2BConfirmationService confirmationService;
    @MockitoBean
    private MpesaShortCodeRepository shortcodes;

    @Test
    void aKnownTokenReachesTheSharedHandler() throws Exception {
        when(shortcodes.findByCallbackToken(TOKEN)).thenReturn(Optional.of(new MpesaShortCode()));
        when(confirmationService.confirm(any(), anyMap()))
                .thenReturn(new C2BConfirmationService.Confirmation(new MpesaIncomingPayment(), false));

        mockMvc.perform(post("/api/v1/callbacks/" + TOKEN + "/c2b/confirmation")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("0"));
        verify(confirmationService).confirm(any(), anyMap());
    }

    @Test
    void theSharedPathStillWorks() throws Exception {
        when(confirmationService.confirm(any(), anyMap()))
                .thenReturn(new C2BConfirmationService.Confirmation(new MpesaIncomingPayment(), false));

        mockMvc.perform(post("/api/v1/callbacks/c2b/confirmation").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownTokenIsRefusedAndNotHandled() throws Exception {
        when(shortcodes.findByCallbackToken(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/callbacks/" + "f".repeat(64) + "/c2b/confirmation")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/callbacks/" + TOKEN.toUpperCase() + "/c2b/confirmation")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(confirmationService, never()).confirm(any(), anyMap());
    }

    @Test
    void theIpAllowListStillAppliesToTokenPaths() throws Exception {
        when(shortcodes.findByCallbackToken(TOKEN)).thenReturn(Optional.of(new MpesaShortCode()));

        mockMvc.perform(post("/api/v1/callbacks/" + TOKEN + "/c2b/confirmation")
                        .with(request -> {
                            request.setRemoteAddr("10.9.8.7");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(confirmationService, never()).confirm(any(), anyMap());
    }
}
