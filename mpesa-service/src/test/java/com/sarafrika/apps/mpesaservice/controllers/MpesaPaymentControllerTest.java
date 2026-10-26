package com.sarafrika.apps.mpesaservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkPushRequest;
import com.sarafrika.apps.mpesaservice.dtos.StkPushResponse;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MpesaPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class MpesaPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MpesaDarajaService mpesaDarajaService;

    @MockBean
    private MpesaIncomingPaymentService incomingPaymentService;

    @Test
    void initiateStkPush_returnsDarajaResponseWithCheckoutId() throws Exception {
        UUID shortcodeUuid = UUID.randomUUID();
        StkPushResponse darajaResponse = new StkPushResponse(
                "21605-295434-4",
                "ws_CO_04112017184930742",
                "0",
                "Success. Request accepted for processing",
                "Check your phone for the M-PESA pin prompt");

        when(mpesaDarajaService.initiateSTKPush(eq(shortcodeUuid), eq("254708374149"),
                any(BigDecimal.class), any(), any()))
                .thenReturn(MpesaApiResponse.success(darajaResponse, HttpStatus.OK.value()));

        StkPushRequest request = new StkPushRequest(
                shortcodeUuid, "254708374149", new BigDecimal("100.00"), "INV-001", "Payment for order");

        mockMvc.perform(post("/api/v1/mpesa/stk-push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.CheckoutRequestID").value("ws_CO_04112017184930742"));
    }

    @Test
    void getPaymentByCheckoutRequestId_returnsPaymentWhenPresent() throws Exception {
        String checkoutId = "ws_CO_04112017184930742";
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setCheckoutRequestId(checkoutId);
        payment.setTransactionId("QDR123ABCD");
        payment.setPaymentType(IncomingPaymentType.STK_PUSH);
        payment.setStatus(IncomingPaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100.00"));

        when(incomingPaymentService.findByCheckoutRequestId(checkoutId)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/api/v1/mpesa/payments/by-checkout/{id}", checkoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkout_request_id").value(checkoutId))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transaction_id").value("QDR123ABCD"));
    }

    @Test
    void getPaymentByCheckoutRequestId_returns404WhenMissing() throws Exception {
        when(incomingPaymentService.findByCheckoutRequestId(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/mpesa/payments/by-checkout/{id}", "missing"))
                .andExpect(status().isNotFound());
    }
}
