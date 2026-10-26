package com.sarafrika.apps.mpesaservice.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.services.collections.C2BConfirmationService;
import com.sarafrika.apps.mpesaservice.services.collections.C2BResultCodes;
import com.sarafrika.apps.mpesaservice.services.collections.C2BValidationService;
import com.sarafrika.apps.mpesaservice.services.collections.ValidationDecision;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Safaricom gets "0" or a C2B000xx code from validation, and an ack for every stored confirmation. */
@WebMvcTest(C2BCallbackController.class)
@Import(CallbackSecurityConfig.class)
class C2BCallbackControllerTest {

    private static final String BODY = """
            {"TransactionType":"Pay Bill","TransID":"TJQ4K2L9XA","TransTime":"20261026143208","TransAmount":"4500.00",
             "BusinessShortCode":"600100","BillRefNumber":"ELM-004512","MSISDN":"254712345612","FirstName":"JANE"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private C2BValidationService validationService;
    @MockitoBean
    private C2BConfirmationService confirmationService;

    @Test
    void validationAnswersAcceptedOrTheRejectionCode() throws Exception {
        when(validationService.validate(any())).thenReturn(ValidationDecision.accept("ok"));
        mockMvc.perform(post("/api/v1/callbacks/c2b/validation").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("0"))
                .andExpect(jsonPath("$.ResultDesc").value("Accepted"));

        when(validationService.validate(any()))
                .thenReturn(ValidationDecision.reject(C2BResultCodes.INVALID_ACCOUNT, "no match"));
        mockMvc.perform(post("/api/v1/callbacks/c2b/validation").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.ResultCode").value("C2B00012"))
                .andExpect(jsonPath("$.ResultDesc").value("Rejected"));

        when(validationService.validate(any())).thenThrow(new IllegalStateException("boom"));
        mockMvc.perform(post("/api/v1/callbacks/c2b/validation").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.ResultCode").value("C2B00016"));
    }

    @Test
    void aConfirmationIsStoredWithItsRawBodyAndAcknowledged() throws Exception {
        when(confirmationService.confirm(any(), anyMap()))
                .thenReturn(new C2BConfirmationService.Confirmation(new MpesaIncomingPayment(), false));

        mockMvc.perform(post("/api/v1/callbacks/c2b/confirmation").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("0"));
        verify(confirmationService).confirm(any(), anyMap());
    }

    @Test
    void aConfirmationThatRacedAnIdenticalOneIsStillAcknowledged() throws Exception {
        when(confirmationService.confirm(any(), anyMap())).thenThrow(new DataIntegrityViolationException("dup"));
        when(confirmationService.findByTransId("TJQ4K2L9XA")).thenReturn(Optional.of(new MpesaIncomingPayment()));

        mockMvc.perform(post("/api/v1/callbacks/c2b/confirmation").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.ResultCode").value("0"));
    }

    @Test
    void aConfirmationThatCannotBeStoredIsNotAcknowledgedAsSuccess() throws Exception {
        when(confirmationService.confirm(any(), anyMap())).thenThrow(new IllegalArgumentException("no amount"));

        mockMvc.perform(post("/api/v1/callbacks/c2b/confirmation").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.ResultCode").value("00000001"));
    }
}
