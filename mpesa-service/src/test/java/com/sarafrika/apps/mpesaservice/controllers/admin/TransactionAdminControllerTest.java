package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.StatusQueryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TransactionDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionFeedService;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionStatusService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionAdminController.class)
@Import(CallbackSecurityConfig.class)
class TransactionAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionFeedService feedService;
    @MockitoBean
    private TransactionStatusService statusService;

    private static final UUID TX = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerPagesTheFeedWithFilters() throws Exception {
        TransactionDto row = new TransactionDto(TX, "in", "C2B", "SUCCESS", null, "600100", "Elimika Fees",
                "TJQ4K2L9XA", null, null, null, "ELM-004512", "254712345612", new BigDecimal("4500.00"), 0, null,
                null, "JANE KAMAU", "PAYBILL", OffsetDateTime.parse("2026-10-26T14:32:08+03:00"), null, null);
        when(feedService.list(any(), any())).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/v1/admin/transactions").param("page", "1").param("size", "10")
                        .param("sort", "amount,asc").param("direction", "in").param("type", "C2B")
                        .param("status", "SUCCESS").param("q", "ELM").param("phone", "0712345612")
                        .param("from", "2026-10-26T00:00:00+03:00").param("to", "2026-10-27T00:00:00+03:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].uuid").value(TX.toString()))
                .andExpect(jsonPath("$.items[0].direction").value("in"))
                .andExpect(jsonPath("$.items[0].account_reference").value("ELM-004512"))
                .andExpect(jsonPath("$.total_items").value(11))
                .andExpect(jsonPath("$.page").value(1));

        ArgumentCaptor<TransactionFeedService.Filter> filter = ArgumentCaptor.forClass(TransactionFeedService.Filter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(feedService).list(filter.capture(), pageable.capture());
        assertThat(filter.getValue().direction()).isEqualTo("in");
        assertThat(filter.getValue().q()).isEqualTo("ELM");
        assertThat(filter.getValue().from()).isEqualTo(OffsetDateTime.parse("2026-10-26T00:00:00+03:00"));
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(1, 10,
                Sort.by(Sort.Order.asc("amount"), Sort.Order.desc("id"))));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void theFeedDefaultsTo25NewestFirstAndRefusesUnknownSorts() throws Exception {
        when(feedService.list(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        mockMvc.perform(get("/api/v1/admin/transactions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(25));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(feedService).list(any(), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        mockMvc.perform(get("/api/v1/admin/transactions").param("sort", "phone,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("sort"));
        mockMvc.perform(get("/api/v1/admin/transactions").param("from", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("from"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aBadFilterIsAFieldError() throws Exception {
        when(feedService.list(any(), any())).thenThrow(new InvalidRequestException("type", "must be one of B2B, B2C"));
        mockMvc.perform(get("/api/v1/admin/transactions").param("type", "REVERSAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("type"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void anUnknownTransactionIs404() throws Exception {
        when(feedService.detail(TX)).thenThrow(new NotFoundException("Transaction not found"));
        mockMvc.perform(get("/api/v1/admin/transactions/" + TX))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorQueriesTheStatusAndGets202() throws Exception {
        when(statusService.request(TX)).thenReturn(new StatusQueryDto(UUID.randomUUID(), "TRANSACTION_STATUS",
                "PENDING", null, null, null, null, "user", OffsetDateTime.now(), null));
        mockMvc.perform(post("/api/v1/admin/transactions/" + TX + "/query-status"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void safaricomFailuresAre502Or503AndConflictsAre409() throws Exception {
        when(statusService.request(TX)).thenThrow(UpstreamException.badGateway("Safaricom refused the status query"));
        mockMvc.perform(post("/api/v1/admin/transactions/" + TX + "/query-status"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Safaricom refused the status query"));

        doThrow(UpstreamException.unavailable("Safaricom could not be reached")).when(statusService).request(TX);
        mockMvc.perform(post("/api/v1/admin/transactions/" + TX + "/query-status"))
                .andExpect(status().isServiceUnavailable());

        doThrow(new ConflictException("no initiator")).when(statusService).request(TX);
        mockMvc.perform(post("/api/v1/admin/transactions/" + TX + "/query-status"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerMayNotQueryTheStatus() throws Exception {
        mockMvc.perform(post("/api/v1/admin/transactions/" + TX + "/query-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(statusService);
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void financeMayReadButNotQuery() throws Exception {
        when(feedService.list(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        mockMvc.perform(get("/api/v1/admin/transactions")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/transactions/" + TX + "/query-status")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "some-other-role")
    void withoutAConsoleRoleNothingIsVisible() throws Exception {
        mockMvc.perform(get("/api/v1/admin/transactions")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/transactions/" + TX)).andExpect(status().isForbidden());
        verifyNoInteractions(feedService);
    }

    @Test
    void anonymousCallersAreTurnedAway() throws Exception {
        mockMvc.perform(get("/api/v1/admin/transactions")).andExpect(status().is4xxClientError());
        verifyNoInteractions(feedService);
    }
}
