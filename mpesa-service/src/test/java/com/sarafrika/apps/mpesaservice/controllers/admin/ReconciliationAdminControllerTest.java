package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.services.reconciliation.PullTransactionsService;
import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationResolver;
import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationService;
import com.sarafrika.apps.mpesaservice.services.reconciliation.StatementParseException;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionKind;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationTrigger;
import java.time.LocalDate;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@WebMvcTest(ReconciliationAdminController.class)
@Import(CallbackSecurityConfig.class)
class ReconciliationAdminControllerTest {

    private static final UUID SHORTCODE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ID = UUID.fromString("99999999-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReconciliationService service;
    @MockitoBean
    private ReconciliationResolver resolver;
    @MockitoBean
    private PullTransactionsService pull;

    private MockMultipartHttpServletRequestBuilder upload() {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/v1/admin/reconciliation/runs")
                .file(new MockMultipartFile("file", "statement.csv", "text/csv", "Receipt No.\n".getBytes()))
                .part(new MockPart("shortcode_uuid", SHORTCODE.toString().getBytes()))
                .part(new MockPart("from", "2026-10-25".getBytes()))
                .part(new MockPart("to", "2026-10-25".getBytes()));
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void financeUploadsAStatement() throws Exception {
        mockMvc.perform(upload()).andExpect(status().isCreated());
        verify(service).reconcileStatement(eq(SHORTCODE), eq(LocalDate.of(2026, 10, 25)), eq(LocalDate.of(2026, 10, 25)),
                eq("statement.csv"), any());
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void operatorsAndViewersCannotUploadOrResolve() throws Exception {
        mockMvc.perform(upload()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to do this"));
        mockMvc.perform(post("/api/v1/admin/reconciliation/exceptions/{id}/backfill", ID)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/reconciliation/exceptions/{id}/merge", ID)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/reconciliation/exceptions/{id}/stk-candidates", ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/reconciliation/exceptions/{id}/attach-stk-receipt", ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"payment_uuid\":\"" + ID + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/reconciliation/exceptions/{id}/query-status", ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/reconciliation/exceptions/{id}/resolve", ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/reconciliation/runs/pull").contentType(MediaType.APPLICATION_JSON)
                .content("{\"shortcode_uuid\":\"" + SHORTCODE + "\",\"from\":\"2026-10-25T00:00:00+03:00\","
                        + "\"to\":\"2026-10-26T00:00:00+03:00\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service, resolver);
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void viewersReadRunsAndExceptionsWithFilters() throws Exception {
        when(service.listRuns(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        when(service.listExceptions(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(),
                PageRequest.of(0, 25), 0));
        mockMvc.perform(get("/api/v1/admin/reconciliation/runs").param("shortcode_uuid", SHORTCODE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(25));
        mockMvc.perform(get("/api/v1/admin/reconciliation/runs/{id}/exceptions", ID).param("kind", "AMOUNT_MISMATCH")
                        .param("status", "OPEN").param("sort", "statement_amount,desc"))
                .andExpect(status().isOk());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).listExceptions(eq(ID), eq(ReconciliationExceptionKind.AMOUNT_MISMATCH),
                eq(ReconciliationExceptionStatus.OPEN), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("statementAmount"),
                Sort.Order.desc("id")));

        mockMvc.perform(get("/api/v1/admin/reconciliation/runs").param("sort", "amount"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field_errors[0].field").value("sort"));
        mockMvc.perform(get("/api/v1/admin/reconciliation/runs/{id}/exceptions", ID).param("kind", "NOPE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/reconciliation/exceptions/{id}/backfill", ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void unreadableLinesComeBackAsFieldErrors() throws Exception {
        when(service.reconcileStatement(any(), any(), any(), any(), any())).thenThrow(new StatementParseException(
                "2 lines could not be read; nothing was reconciled", List.of(
                        new StatementParseException.RowError(9, "Paid In 'abc' is not an amount"),
                        new StatementParseException.RowError(12, "Completion Time is empty"))));
        mockMvc.perform(upload()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("2 lines could not be read; nothing was reconciled"))
                .andExpect(jsonPath("$.field_errors[0].field").value("line 9"))
                .andExpect(jsonPath("$.field_errors[1].message").value("Completion Time is empty"));

        doThrow(new StatementParseException("This is not an M-Pesa organisation statement"))
                .when(service).reconcileStatement(any(), any(), any(), any(), any());
        mockMvc.perform(upload()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("file"));
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void financeAttachesAStatementReceiptToAnStkPayment() throws Exception {
        UUID payment = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/admin/reconciliation/exceptions/{id}/stk-candidates", ID))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/reconciliation/exceptions/{id}/attach-stk-receipt", ID)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("payment_uuid"));
        mockMvc.perform(post("/api/v1/admin/reconciliation/exceptions/{id}/attach-stk-receipt", ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"payment_uuid\":\"" + payment + "\"}"))
                .andExpect(status().isOk());
        verify(resolver).listStkCandidates(ID);
        verify(resolver).attachStkReceipt(ID, payment);
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void badPartsAndMissingNotesAreFieldErrors() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/reconciliation/runs")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", new byte[] {1}))
                        .part(new MockPart("shortcode_uuid", "nope".getBytes()))
                        .part(new MockPart("from", "2026-10-25".getBytes()))
                        .part(new MockPart("to", "2026-10-25".getBytes())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field_errors[0].field").value("shortcode_uuid"));
        mockMvc.perform(post("/api/v1/admin/reconciliation/exceptions/{id}/resolve", ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field_errors[0].field").value("note"));
        mockMvc.perform(post("/api/v1/admin/reconciliation/shortcodes/{id}/pull-transactions/register", SHORTCODE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nominated_number\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("nominated_number"));
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void pullTransactionsIsAConflictWhileTheFeatureIsOff() throws Exception {
        when(service.reconcilePull(any(), any(), any(), eq(ReconciliationTrigger.MANUAL)))
                .thenThrow(new ConflictException("Pull Transactions is not enabled for this paybill"));
        mockMvc.perform(post("/api/v1/admin/reconciliation/runs/pull").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortcode_uuid\":\"" + SHORTCODE + "\",\"from\":\"2026-10-25T00:00:00+03:00\","
                                + "\"to\":\"2026-10-26T00:00:00+03:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Pull Transactions is not enabled for this paybill"));
        verify(service).reconcilePull(SHORTCODE, LocalDate.of(2026, 10, 25).atStartOfDay(),
                LocalDate.of(2026, 10, 26).atStartOfDay(), ReconciliationTrigger.MANUAL);
    }
}
