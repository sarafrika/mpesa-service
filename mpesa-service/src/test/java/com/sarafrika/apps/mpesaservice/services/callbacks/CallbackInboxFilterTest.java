package com.sarafrika.apps.mpesaservice.services.callbacks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.controllers.C2BCallbackController;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackAcks.Processing;
import com.sarafrika.apps.mpesaservice.services.collections.C2BConfirmationService;
import com.sarafrika.apps.mpesaservice.services.collections.C2BResultCodes;
import com.sarafrika.apps.mpesaservice.services.collections.C2BValidationService;
import com.sarafrika.apps.mpesaservice.services.collections.ValidationDecision;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Every callback is in the inbox before its handler runs and is marked by how the handler did. */
@WebMvcTest(C2BCallbackController.class)
@Import(CallbackSecurityConfig.class)
class CallbackInboxFilterTest {

    private static final String CONFIRMATION = "/api/v1/callbacks/c2b/confirmation";
    private static final String BODY = """
            {"TransactionType":"Pay Bill","TransID":"TJQ4K2L9XA","TransTime":"20261026143208","TransAmount":"4500.00",
             "BusinessShortCode":"600100","BillRefNumber":"ELM-004512","MSISDN":"254712345612","FirstName":"JANE"}""";
    private static final UUID ENTRY = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CallbackInboxRecorder recorder;
    @MockitoBean
    private C2BValidationService validationService;
    @MockitoBean
    private C2BConfirmationService confirmationService;

    @Test
    void theBodyIsRecordedBeforeTheHandlerRunsAndMarkedProcessedAfter() throws Exception {
        when(recorder.received(any(), any(), anyMap(), any())).thenReturn(ENTRY);

        mockMvc.perform(post(CONFIRMATION).contentType(MediaType.APPLICATION_JSON).header("User-Agent", "Safaricom")
                        .header("Authorization", "Bearer secret").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("0"));

        InOrder order = inOrder(recorder, confirmationService);
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        order.verify(recorder).received(eq(CONFIRMATION), eq("127.0.0.1"), headers.capture(), body.capture());
        order.verify(confirmationService).confirm(any(), anyMap());
        order.verify(recorder).finished(ENTRY, Processing.success());
        assertThat(new String(body.getValue(), StandardCharsets.UTF_8)).isEqualTo(BODY);
        assertThat(headers.getValue()).containsEntry("User-Agent", "Safaricom").doesNotContainKey("Authorization");
    }

    @Test
    void aHandlerFailureIsAckedToSafaricomButMarkedFailedWithItsReason() throws Exception {
        when(recorder.received(any(), any(), anyMap(), any())).thenReturn(ENTRY);
        when(confirmationService.confirm(any(), anyMap())).thenThrow(new IllegalStateException("database is down"));

        mockMvc.perform(post(CONFIRMATION).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("00000001"));

        ArgumentCaptor<Processing> processing = ArgumentCaptor.forClass(Processing.class);
        verify(recorder).finished(eq(ENTRY), processing.capture());
        assertThat(processing.getValue().ok()).isFalse();
        assertThat(processing.getValue().error()).isEqualTo("IllegalStateException: database is down");
    }

    @Test
    void anInboxThatCannotRecordNeverStopsTheAck() throws Exception {
        when(recorder.received(any(), any(), anyMap(), any())).thenThrow(new IllegalStateException("inbox down"));

        mockMvc.perform(post(CONFIRMATION).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("0"));

        verify(confirmationService).confirm(any(), anyMap());
        verify(recorder, never()).finished(any(), any());
    }

    @Test
    void aFailingOutcomeWriteStillAcks() throws Exception {
        when(recorder.received(any(), any(), anyMap(), any())).thenReturn(ENTRY);
        org.mockito.Mockito.doThrow(new IllegalStateException("inbox down")).when(recorder).finished(any(), any());

        mockMvc.perform(post(CONFIRMATION).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("0"));
    }

    @Test
    void aValidationRejectionIsADecisionButAValidationErrorIsAFailure() throws Exception {
        when(recorder.received(any(), any(), anyMap(), any())).thenReturn(ENTRY);
        when(validationService.validate(any()))
                .thenReturn(ValidationDecision.reject(C2BResultCodes.INVALID_ACCOUNT, "no match"));
        mockMvc.perform(post("/api/v1/callbacks/c2b/validation").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.ResultCode").value("C2B00012"));
        verify(recorder).finished(ENTRY, Processing.success());

        when(validationService.validate(any())).thenThrow(new IllegalStateException("rules unreadable"));
        mockMvc.perform(post("/api/v1/callbacks/c2b/validation").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.ResultCode").value("C2B00016"));
        verify(recorder).finished(ENTRY, Processing.failure("IllegalStateException: rules unreadable"));
    }

    @Test
    void anUnreadableBodyIsStillRecordedAndMarkedFailed() throws Exception {
        when(recorder.received(any(), any(), anyMap(), any())).thenReturn(ENTRY);

        mockMvc.perform(post(CONFIRMATION).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isOk());

        verify(recorder).received(eq(CONFIRMATION), any(), anyMap(), eq("{not json".getBytes(StandardCharsets.UTF_8)));
        ArgumentCaptor<Processing> processing = ArgumentCaptor.forClass(Processing.class);
        verify(recorder).finished(eq(ENTRY), processing.capture());
        assertThat(processing.getValue().ok()).isFalse();
        assertThat(processing.getValue().error()).startsWith("Acknowledged with ResultCode 00000001");
    }
}
