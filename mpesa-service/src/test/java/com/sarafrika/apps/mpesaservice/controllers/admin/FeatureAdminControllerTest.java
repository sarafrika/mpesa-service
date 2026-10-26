package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.features.ShortcodeFeatureDto;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.services.features.FeatureAdminService;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FeatureAdminController.class)
@Import(CallbackSecurityConfig.class)
class FeatureAdminControllerTest {

    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String BASE = "/api/v1/admin/shortcodes/" + ID + "/features";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeatureAdminService features;

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerListsAllFiveFeatures() throws Exception {
        when(features.list(ID)).thenReturn(Arrays.stream(DarajaFeature.values())
                .map(f -> new ShortcodeFeatureDto(f, false, null, null)).toList());

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].feature").value("PULL_TRANSACTIONS"))
                .andExpect(jsonPath("$[0].enabled").value(false));
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void anAdminSwitchesAFeatureOn() throws Exception {
        when(features.update(ID, DarajaFeature.TAX_REMITTANCE, true)).thenReturn(
                new ShortcodeFeatureDto(DarajaFeature.TAX_REMITTANCE, true, "admin", OffsetDateTime.now()));

        mockMvc.perform(put(BASE + "/TAX_REMITTANCE").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.updated_by").value("admin"));
        verify(features).update(ID, DarajaFeature.TAX_REMITTANCE, true);
    }

    @Test
    @WithMockUser(roles = {"mpesa-operator", "mpesa-finance"})
    void onlyAnAdminSwitches() throws Exception {
        mockMvc.perform(put(BASE + "/RATIBA").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(features);
    }

    @Test
    void anonymousCallersAreRefused() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void unknownFeaturesAndMissingValuesAre400() throws Exception {
        mockMvc.perform(put(BASE + "/BILL_MANAGER").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}")).andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/RATIBA").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("enabled"));
        verify(features, org.mockito.Mockito.never()).update(eq(ID), eq(DarajaFeature.RATIBA), anyBoolean());
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void anUnknownShortcodeIs404() throws Exception {
        when(features.list(ID)).thenThrow(new NotFoundException("Shortcode not found"));
        mockMvc.perform(get(BASE)).andExpect(status().isNotFound());
    }
}
