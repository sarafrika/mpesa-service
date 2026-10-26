package com.sarafrika.apps.mpesaservice.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.services.approvals.reversal.ReversalCallbackService;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionStatusService;
import com.sarafrika.apps.mpesaservice.dtos.AccountBalanceResultData;
import com.sarafrika.apps.mpesaservice.dtos.B2BResult;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.treasury.B2bTransferService;
import com.sarafrika.apps.mpesaservice.services.treasury.BalanceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    @MockitoBean
    private BalanceService balanceService;
    @MockitoBean
    private B2bTransferService b2bTransferService;
    @MockitoBean
    private ReversalCallbackService reversalCallbacks;

    @ParameterizedTest
    @ValueSource(strings = {"/b2c/result", "/b2b/result", "/transaction-status/result", "/account-balance/result",
            "/reversal/result", "/b2b/timeout", "/account-balance/timeout"})
    void malformedResultsAreStillAcknowledged(String path) throws Exception {
        mockMvc.perform(post("/api/v1/callbacks" + path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/callbacks" + path).contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isOk());
    }

    // The failure sample on developer.safaricom.co.ke/apis/BusinessPayBill: ResultParameter is one object, not a list.
    @Test
    void aB2bFailureResultWithASingleParameterReachesTheTransfer() throws Exception {
        mockMvc.perform(post("/api/v1/callbacks/b2b/result").contentType(MediaType.APPLICATION_JSON).content("""
                        {"Result":{"ResultType":0,"ResultCode":2001,"ResultDesc":"The initiator information is invalid.",
                        "OriginatorConversationID":"12337-23509183-5","ConversationID":"AG_20200120_0000657265d5fa9ae5c0",
                        "TransactionID":"OAK0000000","ResultParameters":{"ResultParameter":{"Key":"BOCompletedTime",
                        "Value":20200120164825}}}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("00000000"));

        ArgumentCaptor<B2BResult> result = ArgumentCaptor.forClass(B2BResult.class);
        verify(b2bTransferService).applyResult(result.capture(), anyString());
        assertThat(result.getValue().resultCode()).isEqualTo(2001);
        assertThat(result.getValue().resultParameters().getParameterValue("BOCompletedTime")).isEqualTo("20200120164825");
    }

    @Test
    void anAccountBalanceResultReachesItsSnapshot() throws Exception {
        mockMvc.perform(post("/api/v1/callbacks/account-balance/result").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"Result":{"ResultType":"0","ResultCode":"0","ResultDesc":"The service request is processed successfully",
                                "OriginatorConversationID":"16917-22577599-3","ConversationID":"AG_20200206_00005e091a8ec6b9eac5",
                                "TransactionID":"OA90000000","ResultParameters":{"ResultParameter":[{"Key":"AccountBalance",
                                "Value":"Utility Account|KES|228037.00|228037.00|0.00|0.00"},{"Key":"BOCompletedTime","Value":"20200109125710"}]},
                                "ReferenceData":{"ReferenceItem":{"Key":"QueueTimeoutURL","Value":"https://example.test"}}}}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<AccountBalanceResultData> result = ArgumentCaptor.forClass(AccountBalanceResultData.class);
        verify(balanceService).applyResult(result.capture(), anyString());
        assertThat(result.getValue().resultParameters().getAccountBalance()).startsWith("Utility Account|KES|");
    }

    @Test
    void timeoutsAreReadFlatOrWrappedInResult() throws Exception {
        mockMvc.perform(post("/api/v1/callbacks/b2b/timeout").contentType(MediaType.APPLICATION_JSON).content("""
                        {"OriginatorConversationID":"OC-1","ConversationID":"AG_1","ResultDesc":"timed out"}"""))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/callbacks/account-balance/timeout").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"Result":{"ConversationID":"AG_2"}}"""))
                .andExpect(status().isOk());

        verify(b2bTransferService).applyTimeout(eq("OC-1"), eq("AG_1"), eq("timed out"));
        verify(balanceService).applyTimeout(isNull(), eq("AG_2"), isNull());
    }

    @Test
    void aReversalResultWithAStringResultCodeReachesTheService() throws Exception {
        String body = """
                {"Result": {"ResultType": 0, "ResultCode": "R000002", "ResultDesc": "The OriginalTransactionID is invalid.",
                  "OriginatorConversationID": "3124-481d", "ConversationID": "AG_2021", "TransactionID": "SKE0000000"}}
                """;
        mockMvc.perform(post("/api/v1/callbacks/reversal/result").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(reversalCallbacks).applyResult(argThat(node -> "R000002".equals(node.path("Result")
                .path("ResultCode").asText())), anyString());
    }

    @Test
    void aReversalTimeoutIsMatchedByItsConversationIds() throws Exception {
        String body = """
                {"Result": {"ResultType": 1, "ResultCode": 1, "ResultDesc": "The service request has timed out.",
                  "OriginatorConversationID": "3124-481d", "ConversationID": "AG_2021"}}
                """;
        mockMvc.perform(post("/api/v1/callbacks/reversal/timeout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(reversalCallbacks).applyTimeout(eq("3124-481d"), eq("AG_2021"), eq("The service request has timed out."));
    }
}
