package com.sarafrika.apps.mpesaservice.controllers;

import static com.sarafrika.apps.mpesaservice.controllers.MachineApiTokens.console;
import static com.sarafrika.apps.mpesaservice.controllers.MachineApiTokens.grant;
import static com.sarafrika.apps.mpesaservice.controllers.MachineApiTokens.product;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkPushRequest;
import com.sarafrika.apps.mpesaservice.dtos.StkPushResponse;
import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.services.products.ProductGrantGuard;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** With grants enforced (the default): products use only what they were granted; console users read freely. */
@WebMvcTest(MpesaPaymentController.class)
@Import({CallbackSecurityConfig.class, ProductGrantGuard.class})
class MpesaPaymentControllerTest {

    private static final UUID PAYBILL = UUID.randomUUID();
    private static final UUID OTHER_PAYBILL = UUID.randomUUID();
    private static final String CHECKOUT = "ws_CO_04112017184930742";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MpesaDarajaService mpesaDarajaService;
    @MockitoBean
    private MpesaIncomingPaymentService incomingPaymentService;
    @MockitoBean
    private ClientShortcodeGrantRepository grants;

    @BeforeEach
    void elimikaMayPushAndLookUpOnOnePaybill() {
        ClientShortcodeGrant elimika = grant("elimika-backend", PAYBILL, GrantOperation.STK_PUSH,
                GrantOperation.STATUS_LOOKUP);
        when(grants.findByClientIdAndShortcodeUuid(anyString(), any())).thenReturn(Optional.empty());
        when(grants.findByClientIdAndShortcodeUuid("elimika-backend", PAYBILL)).thenReturn(Optional.of(elimika));
        when(grants.findByClientIdAndActiveTrue(anyString())).thenReturn(List.of());
        when(grants.findByClientIdAndActiveTrue("elimika-backend")).thenReturn(List.of(elimika));
        when(grants.findByClientIdAndActiveTrue("shop-api"))
                .thenReturn(List.of(grant("shop-api", OTHER_PAYBILL, GrantOperation.STATUS_LOOKUP)));
    }

    @Test
    void aGrantedProductStartsAnStkPush() throws Exception {
        when(mpesaDarajaService.initiateSTKPush(eq(PAYBILL), eq("254708374149"), any(BigDecimal.class), any(), any()))
                .thenReturn(MpesaApiResponse.success(new StkPushResponse("21605-295434-4", CHECKOUT, "0",
                        "Success. Request accepted for processing", "Check your phone"), HttpStatus.OK.value()));

        mockMvc.perform(post("/api/v1/mpesa/stk-push").with(product("elimika-backend"))
                        .contentType(MediaType.APPLICATION_JSON).content(stkBody(PAYBILL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.CheckoutRequestID").value(CHECKOUT));
    }

    @Test
    void anStkPushOnAPaybillThatWasNotGrantedIsRefusedInTheEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/mpesa/stk-push").with(product("elimika-backend"))
                        .contentType(MediaType.APPLICATION_JSON).content(stkBody(OTHER_PAYBILL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.httpStatus").value(403))
                .andExpect(jsonPath("$.error.errorCode").value("GRANT_REQUIRED"))
                .andExpect(jsonPath("$.error.errorMessage").value(org.hamcrest.Matchers.containsString(
                        "'elimika-backend' is not granted STK_PUSH")));
        mockMvc.perform(post("/api/v1/mpesa/stk-push").with(product("shop-api"))
                        .contentType(MediaType.APPLICATION_JSON).content(stkBody(PAYBILL)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mpesaDarajaService);
    }

    @Test
    void consoleUsersStillNeedAGrantToMoveMoney() throws Exception {
        mockMvc.perform(post("/api/v1/mpesa/stk-push").with(console("mpesa-admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(stkBody(PAYBILL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.errorCode").value("GRANT_REQUIRED"));
        verifyNoInteractions(mpesaDarajaService);
    }

    @Test
    void lookupsNeedStatusLookupOnThePaymentsShortcode() throws Exception {
        when(incomingPaymentService.findByCheckoutRequestId(CHECKOUT)).thenReturn(Optional.of(payment(PAYBILL)));

        mockMvc.perform(get("/api/v1/mpesa/payments/by-checkout/{id}", CHECKOUT).with(product("elimika-backend")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkout_request_id").value(CHECKOUT))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        mockMvc.perform(get("/api/v1/mpesa/payments/by-checkout/{id}", CHECKOUT).with(product("shop-api")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.errorCode").value("GRANT_REQUIRED"));
        mockMvc.perform(get("/api/v1/mpesa/payments/by-checkout/{id}", CHECKOUT).with(product("stranger")))
                .andExpect(status().isForbidden());
    }

    @Test
    void consoleViewersLookUpWithoutGrants() throws Exception {
        when(incomingPaymentService.findByCheckoutRequestId(CHECKOUT)).thenReturn(Optional.of(payment(OTHER_PAYBILL)));

        mockMvc.perform(get("/api/v1/mpesa/payments/by-checkout/{id}", CHECKOUT).with(console("mpesa-viewer")))
                .andExpect(status().isOk());
    }

    @Test
    void aMissingPaymentIs404ForAGrantedProduct() throws Exception {
        when(incomingPaymentService.findByCheckoutRequestId(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/mpesa/payments/by-checkout/{id}", "missing").with(product("elimika-backend")))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchesOnlyReturnPaymentsOnGrantedShortcodes() throws Exception {
        MpesaIncomingPayment mine = payment(PAYBILL);
        mine.setTransactionId("TJQ4K2L9XA");
        MpesaIncomingPayment theirs = payment(OTHER_PAYBILL);
        theirs.setTransactionId("TJQ4K2L9XB");
        when(incomingPaymentService.findByAccountReference("INV-001")).thenReturn(List.of(mine, theirs));

        mockMvc.perform(get("/api/v1/mpesa/payments").param("account_reference", "INV-001")
                        .with(product("elimika-backend")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transaction_id").value("TJQ4K2L9XA"));
        mockMvc.perform(get("/api/v1/mpesa/payments").param("account_reference", "INV-001")
                        .with(console("mpesa-viewer")))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/mpesa/payments").param("account_reference", "INV-001")
                        .with(product("stranger")))
                .andExpect(status().isForbidden());
    }

    @Test
    void productsSharingAPaybillSeeOnlyTheirOwnStkRequestsButEveryC2bPayment() throws Exception {
        ClientShortcodeGrant events = grant("events-app", PAYBILL, GrantOperation.STATUS_LOOKUP);
        when(grants.findByClientIdAndShortcodeUuid("events-app", PAYBILL)).thenReturn(Optional.of(events));
        when(grants.findByClientIdAndActiveTrue("events-app")).thenReturn(List.of(events));
        MpesaIncomingPayment stk = payment(PAYBILL);
        stk.setClientId("elimika-backend");
        MpesaIncomingPayment c2b = payment(PAYBILL);
        c2b.setPaymentType(IncomingPaymentType.C2B);
        c2b.setTransactionId("TJQ4K2L9XC");
        when(incomingPaymentService.findByAccountReference("INV-001")).thenReturn(List.of(stk, c2b));
        when(incomingPaymentService.findByCheckoutRequestId(CHECKOUT)).thenReturn(Optional.of(stk));

        mockMvc.perform(get("/api/v1/mpesa/payments").param("account_reference", "INV-001")
                        .with(product("events-app")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transaction_id").value("TJQ4K2L9XC"));
        mockMvc.perform(get("/api/v1/mpesa/payments").param("account_reference", "INV-001")
                        .with(product("elimika-backend")))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/mpesa/payments/by-checkout/{id}", CHECKOUT).with(product("events-app")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/mpesa/payments/by-checkout/{id}", CHECKOUT).with(console("mpesa-viewer")))
                .andExpect(status().isOk());
    }

    @Test
    void callersWithoutATokenAreTurnedAway() throws Exception {
        mockMvc.perform(get("/api/v1/mpesa/payments").param("account_reference", "INV-001"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(incomingPaymentService);
    }

    private String stkBody(UUID shortcode) throws Exception {
        return objectMapper.writeValueAsString(new StkPushRequest(shortcode, "254708374149", new BigDecimal("100.00"),
                "INV-001", "Payment for order"));
    }

    private static MpesaIncomingPayment payment(UUID shortcode) {
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setShortcodeUuid(shortcode);
        payment.setCheckoutRequestId(CHECKOUT);
        payment.setTransactionId("QDR123ABCD");
        payment.setPaymentType(IncomingPaymentType.STK_PUSH);
        payment.setStatus(IncomingPaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100.00"));
        return payment;
    }
}
