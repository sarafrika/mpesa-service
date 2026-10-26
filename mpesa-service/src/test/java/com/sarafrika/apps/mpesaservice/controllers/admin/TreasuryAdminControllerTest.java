package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bSweepSubmittedDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.BalanceSnapshotDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.services.treasury.B2bDestinationService;
import com.sarafrika.apps.mpesaservice.services.treasury.B2bSweepService;
import com.sarafrika.apps.mpesaservice.services.treasury.BalanceService;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceQuerySource;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceSnapshotStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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

@WebMvcTest(TreasuryAdminController.class)
@Import(CallbackSecurityConfig.class)
class TreasuryAdminControllerTest {

    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID DESTINATION = UUID.fromString("650e8400-e29b-41d4-a716-446655440000");
    private static final String BASE = "/api/v1/admin/shortcodes/" + ID;
    private static final String SWEEP = """
            {"destination_uuid":"650e8400-e29b-41d4-a716-446655440000","amount":500000,"reason":"Weekly sweep"}""";
    private static final String DESTINATION_BODY = """
            {"name":"Sarafrika bank","receiver_shortcode":"522522","receiver_type":"PAYBILL","account_reference":"SAR-1"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceService balances;
    @MockitoBean
    private B2bDestinationService destinations;
    @MockitoBean
    private B2bSweepService sweeps;

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorQueriesTheBalanceAndGets202WithThePendingQuery() throws Exception {
        when(balances.requestQuery(ID)).thenReturn(new BalanceSnapshotDto(UUID.randomUUID(), ID,
                BalanceSnapshotStatus.PENDING, BalanceQuerySource.MANUAL, List.of(), null, null, null, null, null,
                "ops", OffsetDateTime.now(), null));

        mockMvc.perform(post(BASE + "/balance-query"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.source").value("MANUAL"));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void darajaFailuresAre502Or503() throws Exception {
        org.mockito.Mockito.doThrow(UpstreamException.badGateway("Safaricom refused")).when(balances).requestQuery(ID);
        mockMvc.perform(post(BASE + "/balance-query")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Safaricom refused"));

        org.mockito.Mockito.doThrow(UpstreamException.unavailable("unreachable")).when(balances).requestQuery(ID);
        mockMvc.perform(post(BASE + "/balance-query")).andExpect(status().isServiceUnavailable());

        org.mockito.Mockito.doThrow(new ConflictException("no initiator")).when(balances).requestQuery(ID);
        mockMvc.perform(post(BASE + "/balance-query")).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerReadsButCannotQuerySetThresholdsOrSweep() throws Exception {
        mockMvc.perform(get(BASE + "/balance")).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/balance/history").param("from", "2026-10-19T00:00:00+03:00"))
                .andExpect(status().isOk());
        when(sweeps.list(eq(ID), isNull(), any())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get(BASE + "/b2b-transfers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total_items").value(0));

        mockMvc.perform(post(BASE + "/balance-query")).andExpect(status().isForbidden());
        mockMvc.perform(patch(BASE + "/float-threshold").contentType(MediaType.APPLICATION_JSON)
                .content("{\"low_float_threshold\":1000}")).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/b2b-sweeps").contentType(MediaType.APPLICATION_JSON).content(SWEEP))
                .andExpect(status().isForbidden());
        verifyNoInteractions(destinations);
        verify(balances, org.mockito.Mockito.never()).requestQuery(any());
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void destinationsAreAdminOnlyToChange() throws Exception {
        mockMvc.perform(post(BASE + "/b2b-destinations").contentType(MediaType.APPLICATION_JSON)
                .content(DESTINATION_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(patch(BASE + "/b2b-destinations/" + DESTINATION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/b2b-destinations/" + DESTINATION)).andExpect(status().isForbidden());
        verifyNoInteractions(destinations);
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void anAdminManagesDestinations() throws Exception {
        mockMvc.perform(post(BASE + "/b2b-destinations").contentType(MediaType.APPLICATION_JSON)
                .content(DESTINATION_BODY)).andExpect(status().isCreated());
        mockMvc.perform(delete(BASE + "/b2b-destinations/" + DESTINATION)).andExpect(status().isNoContent());
        mockMvc.perform(post(BASE + "/b2b-destinations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"receiver_shortcode\":\"12\",\"receiver_type\":\"PAYBILL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'receiver_shortcode')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'name')]").exists());
        when(destinations.list(eq(ID), any(), any())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get(BASE + "/b2b-destinations").param("sort", "amount,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorRequestsASweepThatGoesToApprovals() throws Exception {
        UUID approval = UUID.randomUUID();
        when(sweeps.request(eq(ID), any())).thenReturn(new B2bSweepSubmittedDto(approval, "SWP-ABCDEFGH",
                new BigDecimal("500000")));

        mockMvc.perform(post(BASE + "/b2b-sweeps").contentType(MediaType.APPLICATION_JSON).content(SWEEP))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.approval_request_uuid").value(approval.toString()));

        org.mockito.Mockito.doThrow(new InvalidRequestException("amount", "is too much")).when(sweeps).request(eq(ID), any());
        mockMvc.perform(post(BASE + "/b2b-sweeps").contentType(MediaType.APPLICATION_JSON).content(SWEEP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("amount"));

        mockMvc.perform(post(BASE + "/b2b-sweeps").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'destination_uuid')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'amount')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'reason')]").exists());
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void aNegativeThresholdIsAFieldError() throws Exception {
        mockMvc.perform(patch(BASE + "/float-threshold").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"low_float_threshold\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("low_float_threshold"));
        mockMvc.perform(patch(BASE + "/float-threshold").contentType(MediaType.APPLICATION_JSON)
                .content("{\"low_float_threshold\":null}")).andExpect(status().isOk());
        verify(balances).updateThreshold(ID, null);
    }
}
