package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.ApprovalDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.BulkPayoutUploadDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PreCheckDto;
import com.sarafrika.apps.mpesaservice.exceptions.MakerCheckerException;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalService;
import com.sarafrika.apps.mpesaservice.services.approvals.bulk.BulkPayoutService;
import com.sarafrika.apps.mpesaservice.services.approvals.bulk.BulkPayoutViews;
import com.sarafrika.apps.mpesaservice.services.approvals.reversal.ReversalService;
import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApprovalAdminController.class)
@Import(CallbackSecurityConfig.class)
class ApprovalAdminControllerTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApprovalService approvals;
    @MockitoBean
    private ReversalService reversals;
    @MockitoBean
    private BulkPayoutService bulkPayouts;
    @MockitoBean
    private BulkPayoutViews batches;

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerListsWithStatusAndTypeFilters() throws Exception {
        when(approvals.list(any(), any())).thenReturn(new PageImpl<>(List.of(dto()), PageRequest.of(0, 25), 1));

        mockMvc.perform(get("/api/v1/admin/approvals").param("status", "APPROVED,EXECUTED").param("status", "failed")
                        .param("type", "REVERSAL").param("sort", "amount,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].uuid").value(ID.toString()))
                .andExpect(jsonPath("$.items[0].pre_checks[0].state").value("OK"))
                .andExpect(jsonPath("$.items[0].own_request").value(false));

        ArgumentCaptor<ApprovalService.Filter> filter = ArgumentCaptor.forClass(ApprovalService.Filter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(approvals).list(filter.capture(), pageable.capture());
        assertThat(filter.getValue().statuses()).containsExactly(ApprovalStatus.APPROVED, ApprovalStatus.EXECUTED,
                ApprovalStatus.FAILED);
        assertThat(filter.getValue().type()).isEqualTo("REVERSAL");
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("amount"), Sort.Order.desc("id")));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void unknownStatusesAndSortsAreFieldErrors() throws Exception {
        mockMvc.perform(get("/api/v1/admin/approvals").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("status"));
        mockMvc.perform(get("/api/v1/admin/approvals").param("sort", "maker,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("sort"));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void onlyFinanceDecides() throws Exception {
        mockMvc.perform(post("/api/v1/admin/approvals/{uuid}/approve", ID)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/approvals/{uuid}/reject", ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"no\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(approvals);
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void adminImpliesFinanceAndApproves() throws Exception {
        when(approvals.approve(eq(ID), isNull())).thenReturn(dto());
        mockMvc.perform(post("/api/v1/admin/approvals/{uuid}/approve", ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(ID.toString()));
    }

    @Test
    @WithMockUser(username = "jane", roles = "mpesa-finance")
    void theMakerIsToldWhyTheyCannotApprove() throws Exception {
        when(approvals.approve(eq(ID), eq("fine"))).thenThrow(new MakerCheckerException(
                "You raised this request, so another finance user has to decide it."));
        mockMvc.perform(post("/api/v1/admin/approvals/{uuid}/approve", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"fine\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You raised this request, so another finance user has to "
                        + "decide it."));
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void rejectingWithoutANoteIsAFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/admin/approvals/{uuid}/reject", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("note"));
        verifyNoInteractions(approvals);
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void requestingAReversalNeedsOperator() throws Exception {
        mockMvc.perform(post("/api/v1/admin/approvals/reversals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_uuid\":\"" + ID + "\",\"reason\":\"dup\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reversals);
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorRequestsAReversalWithAReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/approvals/reversals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_uuid\":\"" + ID + "\",\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.length()").value(2));

        when(reversals.request(any())).thenReturn(ID);
        when(approvals.get(ID)).thenReturn(dto());
        mockMvc.perform(post("/api/v1/admin/approvals/reversals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_uuid\":\"" + ID + "\",\"reason\":\"Paid twice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REVERSAL"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerCannotUploadABulkPayout() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/approvals/bulk-payouts")
                        .file(new MockMultipartFile("file", "a.csv", "text/csv", "phone".getBytes()))
                        .param("shortcode_uuid", ID.toString()).param("title", "Oct"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bulkPayouts);
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorUploadsACsvForPreview() throws Exception {
        when(bulkPayouts.upload(any())).thenReturn(new BulkPayoutUploadDto(true, List.of(), 0, 0, BigDecimal.ZERO,
                null, null));
        mockMvc.perform(multipart("/api/v1/admin/approvals/bulk-payouts")
                        .file(new MockMultipartFile("file", "oct.csv", "text/csv", "phone,amount,reference".getBytes()))
                        .param("shortcode_uuid", ID.toString()).param("title", "Oct").param("preview", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value(true));
        ArgumentCaptor<BulkPayoutService.Upload> upload = ArgumentCaptor.forClass(BulkPayoutService.Upload.class);
        verify(bulkPayouts).upload(upload.capture());
        assertThat(upload.getValue().fileName()).isEqualTo("oct.csv");
        assertThat(upload.getValue().preview()).isTrue();
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void batchItemsSortByLineOrAmountOnly() throws Exception {
        when(batches.items(eq(ID), eq("PAID"), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        mockMvc.perform(get("/api/v1/admin/approvals/bulk-payouts/{uuid}/items", ID).param("outcome", "PAID")
                .param("sort", "amount,desc")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/approvals/bulk-payouts/{uuid}/items", ID).param("sort", "phone"))
                .andExpect(status().isBadRequest());
    }

    private static ApprovalDto dto() {
        return new ApprovalDto(ID, "REVERSAL", "WAITING", "Reverse TJQ4K2L1MX", null, "600100", "Elimika Fees",
                new BigDecimal("15000.00"), "Paid twice", "POST /mpesa/reversal/v1/request · TransactionReversal",
                List.of(PreCheckDto.ok("Not reversed before")), "otieno", "Otieno", null, null, null, null,
                OffsetDateTime.parse("2026-10-28T10:00:00+03:00"), null, null,
                OffsetDateTime.parse("2026-10-26T10:00:00+03:00"), false, ID, null, null);
    }
}
