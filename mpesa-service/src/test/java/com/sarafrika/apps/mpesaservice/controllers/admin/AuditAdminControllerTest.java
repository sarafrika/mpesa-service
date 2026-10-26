package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.AuditEventDto;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditAdminController.class)
@Import(CallbackSecurityConfig.class)
class AuditAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditService auditService;

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void anAdminPagesThroughTheTrailWithFilters() throws Exception {
        AuditEventDto event = new AuditEventDto(UUID.randomUUID(), "jane.doe", "Jane Doe", "SHORTCODE_UPDATED",
                "SHORTCODE", "sc-1", objectMapper.readTree("{\"business_name\":\"Old\"}"),
                objectMapper.readTree("{\"business_name\":\"New\",\"passkey\":\"[REDACTED]\"}"), "10.0.0.1",
                Instant.parse("2026-10-27T08:00:00Z"));
        AuditService.AuditQuery query = new AuditService.AuditQuery("jane.doe", null, "SHORTCODE", null);
        when(auditService.list(eq(query), eq(1), eq(10)))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/v1/admin/audit").param("page", "1").param("size", "10")
                        .param("actor", "jane.doe").param("entity_type", "SHORTCODE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actor").value("jane.doe"))
                .andExpect(jsonPath("$.items[0].actor_name").value("Jane Doe"))
                .andExpect(jsonPath("$.items[0].entity_type").value("SHORTCODE"))
                .andExpect(jsonPath("$.items[0].after.passkey").value("[REDACTED]"))
                .andExpect(jsonPath("$.items[0].created_at").value("2026-10-27T08:00:00Z"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total_items").value(11))
                .andExpect(jsonPath("$.total_pages").value(2));
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void pagesDefaultTo25() throws Exception {
        when(auditService.list(eq(new AuditService.AuditQuery(null, null, null, null)), eq(0), eq(25)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.size").value(25));
        verify(auditService).list(eq(new AuditService.AuditQuery(null, null, null, null)), eq(0), eq(25));
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void aPageLargerThan100IsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("size"));
        verifyNoInteractions(auditService);
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerMayNotReadTheTrail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(auditService);
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void financeIsNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallersAreTurnedAway() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")).andExpect(status().is4xxClientError());
        verify(auditService, never()).list(any(), anyInt(), anyInt());
    }
}
