package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.ProductPayoutSubmittedDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.services.daraja.products.B2bCheckoutService;
import com.sarafrika.apps.mpesaservice.services.daraja.products.ProductPayoutService;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DarajaProductAdminController.class)
@Import(CallbackSecurityConfig.class)
class DarajaProductAdminControllerTest {

    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String BASE = "/api/v1/admin/shortcodes/" + ID;
    private static final String TAX = """
            {"amount":3000,"prn":"PRN1234XN","reason":"October VAT"}""";
    private static final String POCHI = """
            {"phone_number":"254705912645","amount":1500,"remarks":"Supplier payment","reason":"Stock"}""";
    private static final String CHECKOUT = """
            {"payer_till":"000001","amount":100,"payment_ref":"INV-1"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductPayoutService payouts;
    @MockitoBean
    private B2bCheckoutService checkouts;

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorRequestsATaxRemittanceThatGoesToApprovals() throws Exception {
        UUID approval = UUID.randomUUID();
        when(payouts.requestTax(eq(ID), any())).thenReturn(new ProductPayoutSubmittedDto(approval,
                new BigDecimal("3000")));

        mockMvc.perform(post(BASE + "/tax-remittances").contentType(MediaType.APPLICATION_JSON).content(TAX))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.approval_request_uuid").value(approval.toString()));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerListsButCannotStartAnything() throws Exception {
        when(payouts.list(eq(ID), eq(DarajaFeature.BUSINESS_TO_POCHI), isNull(), any())).thenReturn(new PageImpl<>(
                java.util.List.of()));
        mockMvc.perform(get(BASE + "/pochi-payouts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(post(BASE + "/tax-remittances").contentType(MediaType.APPLICATION_JSON).content(TAX))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/pochi-payouts").contentType(MediaType.APPLICATION_JSON).content(POCHI))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/b2b-checkouts").contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isForbidden());
        verifyNoInteractions(checkouts);
        verify(payouts, org.mockito.Mockito.never()).requestTax(any(), any());
        verify(payouts, org.mockito.Mockito.never()).requestPochi(any(), any());
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void aSwitchedOffFeatureIs409() throws Exception {
        when(payouts.requestPochi(eq(ID), any())).thenThrow(new ConflictException("Business to Pochi is switched off"));
        when(checkouts.start(eq(ID), any())).thenThrow(new ConflictException("B2B Express Checkout is switched off"));

        mockMvc.perform(post(BASE + "/pochi-payouts").contentType(MediaType.APPLICATION_JSON).content(POCHI))
                .andExpect(status().isConflict());
        mockMvc.perform(post(BASE + "/b2b-checkouts").contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void pochiAmountsOutsideSafaricomsLimitsAreFieldErrors() throws Exception {
        mockMvc.perform(post(BASE + "/pochi-payouts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"phone_number":"0705912645","amount":9,"remarks":"x","reason":"r"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'amount')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'phone_number')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'remarks')]").exists());
        mockMvc.perform(post(BASE + "/pochi-payouts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"phone_number":"254705912645","amount":250001,"remarks":"ok","reason":"r"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("amount"));
        verifyNoInteractions(payouts);
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void aTaxRemittanceNeedsAPrnAndACheckoutATill() throws Exception {
        mockMvc.perform(post(BASE + "/tax-remittances").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount":3000,"reason":"r"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("prn"));
        mockMvc.perform(post(BASE + "/b2b-checkouts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"payer_till":"abc","amount":100,"payment_ref":"INV-1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("payer_till"));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void aRefusedPushIs502() throws Exception {
        when(checkouts.start(eq(ID), any())).thenThrow(UpstreamException.badGateway("Safaricom refused the push"));
        mockMvc.perform(post(BASE + "/b2b-checkouts").contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Safaricom refused the push"));
    }
}
