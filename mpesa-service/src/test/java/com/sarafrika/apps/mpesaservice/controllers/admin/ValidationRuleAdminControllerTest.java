package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationDryRunResult;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationRulesDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.services.collections.ValidationRuleService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ValidationRuleAdminController.class)
@Import(CallbackSecurityConfig.class)
class ValidationRuleAdminControllerTest {

    private static final UUID SC = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String PATH = "/api/v1/admin/shortcodes/" + SC + "/validation-rules";
    private static final String RULES = """
            {"active":true,"account_pattern":"^ELM-[0-9]{6}$","min_amount":10,"max_amount":250000,
             "lookup_url":"https://elimika.example/accounts","lookup_timeout_ms":2000,"on_lookup_failure":"REJECT"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ValidationRuleService ruleService;

    private static ValidationRulesDto saved() {
        return new ValidationRulesDto(SC, true, true, "^ELM-[0-9]{6}$", new BigDecimal("10"),
                new BigDecimal("250000"), "https://elimika.example/accounts", 2000, "REJECT", null, "jane");
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerReadsTheRules() throws Exception {
        when(ruleService.get(SC)).thenReturn(saved());
        mockMvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.account_pattern").value("^ELM-[0-9]{6}$"))
                .andExpect(jsonPath("$.on_lookup_failure").value("REJECT"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerMayNotChangeOrDryRunThem() throws Exception {
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(RULES))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + "/dry-run").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"ELM-1\",\"amount\":10}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ruleService);
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorReplacesThem() throws Exception {
        when(ruleService.update(eq(SC), any())).thenReturn(saved());
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(RULES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void badFieldsComeBackAsFieldErrors() throws Exception {
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"lookup_url\":\"ftp://x\",\"lookup_timeout_ms\":99999,"
                                + "\"on_lookup_failure\":\"REJECT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.length()").value(2));
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isBadRequest());

        when(ruleService.update(eq(SC), any()))
                .thenThrow(new InvalidRequestException("account_pattern", "is not a valid regular expression"));
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(RULES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("account_pattern"));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void aDryRunSaysAcceptOrRejectWithTheCode() throws Exception {
        when(ruleService.dryRun(eq(SC), any()))
                .thenReturn(new ValidationDryRunResult(false, "C2B00012", "Account 'X' does not match"));
        mockMvc.perform(post(PATH + "/dry-run").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"X\",\"amount\":10,\"rules\":" + RULES + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.result_code").value("C2B00012"));

        mockMvc.perform(post(PATH + "/dry-run").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"X\",\"amount\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void anUnknownShortcodeIs404() throws Exception {
        when(ruleService.get(SC)).thenThrow(new NotFoundException("Shortcode not found"));
        mockMvc.perform(get(PATH)).andExpect(status().isNotFound());
    }
}
