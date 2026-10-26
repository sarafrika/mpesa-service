package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackAttachService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackSummaryDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackInboxService;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackReplayService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(CallbackInboxAdminController.class)
@Import(CallbackSecurityConfig.class)
class CallbackInboxAdminControllerTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-26T14:32:08+03:00");
    private static final CallbackDto ROW = new CallbackDto(ID, "/api/v1/callbacks/b2c/result", "B2C_RESULT",
            "196.201.214.200", "FAILED", "IllegalStateException: boom", 1, AT, "AG_1 sar-1", null, AT);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CallbackInboxService inboxService;
    @MockitoBean
    private CallbackReplayService replayService;

    @MockitoBean
    private CallbackAttachService attachService;

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerPagesTheInboxWithFilters() throws Exception {
        when(inboxService.list(any(), any())).thenReturn(new PageImpl<>(List.of(ROW), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/admin/callbacks").param("size", "10").param("status", "FAILED")
                        .param("kind", "B2C_RESULT").param("q", "AG_1").param("sort", "attempts,desc")
                        .param("from", "2026-10-26T00:00:00+03:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].uuid").value(ID.toString()))
                .andExpect(jsonPath("$.items[0].source_ip").value("196.201.214.200"))
                .andExpect(jsonPath("$.total_items").value(1));

        ArgumentCaptor<CallbackInboxService.Filter> filter = ArgumentCaptor.forClass(CallbackInboxService.Filter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(inboxService).list(filter.capture(), pageable.capture());
        assertThat(filter.getValue().status()).isEqualTo("FAILED");
        assertThat(filter.getValue().q()).isEqualTo("AG_1");
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 10,
                Sort.by(Sort.Order.desc("attempts"), Sort.Order.desc("id"))));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void unknownSortsAreRefused() throws Exception {
        mockMvc.perform(get("/api/v1/admin/callbacks").param("sort", "body,asc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inboxService);
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerReadsOneCallbackAndTheSummary() throws Exception {
        when(inboxService.get(ID)).thenReturn(new CallbackDetailDto(ROW, Map.of("User-Agent", "Safaricom"), null, 0,
                false, true, null, false));
        when(inboxService.summary()).thenReturn(new CallbackSummaryDto(AT, 12, 1, 9, 1, 1));

        mockMvc.perform(get("/api/v1/admin/callbacks/" + ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.callback.kind").value("B2C_RESULT"))
                .andExpect(jsonPath("$.replayable").value(true));
        mockMvc.perform(get("/api/v1/admin/callbacks/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.total").value(12));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerCannotReplay() throws Exception {
        mockMvc.perform(post("/api/v1/admin/callbacks/" + ID + "/replay")).andExpect(status().isForbidden());
        verifyNoInteractions(replayService);
    }

    @Test
    void anonymousCallersAreRefused() throws Exception {
        mockMvc.perform(get("/api/v1/admin/callbacks")).andExpect(status().is4xxClientError());
        verifyNoInteractions(inboxService);
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorReplaysAndConflictsAndMissesAreMapped() throws Exception {
        when(replayService.replay(ID)).thenReturn(new CallbackDetailDto(ROW, Map.of(), null, 0, false, false, "x", false));
        mockMvc.perform(post("/api/v1/admin/callbacks/" + ID + "/replay")).andExpect(status().isOk());

        when(replayService.replay(ID)).thenThrow(new ConflictException("Only failed callbacks can be replayed"));
        mockMvc.perform(post("/api/v1/admin/callbacks/" + ID + "/replay")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only failed callbacks can be replayed"));

        when(inboxService.get(ID)).thenThrow(new NotFoundException("No callback"));
        mockMvc.perform(get("/api/v1/admin/callbacks/" + ID)).andExpect(status().isNotFound());
    }
}
