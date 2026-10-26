package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationState;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bResponseType;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ConnectionTestResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.CreateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeCallbackUrlsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeCredentialsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeStatus;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeTotalsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SimulateC2bResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.TestStkResultDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeAdminService;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeC2bService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(ShortcodeAdminController.class)
@Import(CallbackSecurityConfig.class)
class ShortcodeAdminControllerTest {

    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String BASE = "/api/v1/admin/shortcodes";
    private static final String CREATE = """
            {"shortcode":"600100","shortcode_type":"PAYBILL","business_name":"Elimika Fees",
             "environment":"SANDBOX","consumer_key":"key-Qx7d","consumer_secret":"secret-9KfA"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortcodeAdminService shortcodes;
    @MockitoBean
    private ShortcodeC2bService c2b;

    // ---- list, totals, callback URLs, detail (viewer)

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerListsShortcodesWithFiltersPagingAndHealth() throws Exception {
        when(shortcodes.list(any(), any())).thenReturn(new PageImpl<>(List.of(summary()), PageRequest.of(1, 10), 11));

        mockMvc.perform(get(BASE).param("page", "1").param("size", "10").param("sort", "shortcode,desc")
                        .param("type", "PAYBILL").param("environment", "PRODUCTION").param("status", "ACTIVE")
                        .param("q", "elimika").param("client_id", "elimika-backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].business_name").value("Elimika Fees"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].health.c2b_state").value("REGISTERED"))
                .andExpect(jsonPath("$.items[0].health.collected_today").value(902140.0))
                .andExpect(jsonPath("$.items[0].consumer_secret").doesNotExist())
                .andExpect(jsonPath("$.items[0].products[0].client_id").value("elimika-backend"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.total_items").value(11));
        ArgumentCaptor<ShortcodeAdminService.ShortcodeQuery> query =
                ArgumentCaptor.forClass(ShortcodeAdminService.ShortcodeQuery.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(shortcodes).list(query.capture(), pageable.capture());
        assertThat(query.getValue()).isEqualTo(new ShortcodeAdminService.ShortcodeQuery(ShortcodeType.PAYBILL,
                Environment.PRODUCTION, null, ShortcodeStatus.ACTIVE, "elimika", "elimika-backend"));
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(1, 10,
                Sort.by(Sort.Order.desc("shortcode"), Sort.Order.desc("id"))));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void theListDefaultsToNameOrderAndRefusesUnknownSortsAndValues() throws Exception {
        when(shortcodes.list(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        mockMvc.perform(get(BASE).param("size", "500")).andExpect(status().isOk());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(shortcodes).list(any(), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 100,
                Sort.by(Sort.Order.asc("businessName"), Sort.Order.desc("id"))));

        mockMvc.perform(get(BASE).param("sort", "consumer_secret,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("sort"));
        mockMvc.perform(get(BASE).param("type", "BANK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("type"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void totalsCallbackUrlsAndDetailAreReadable() throws Exception {
        when(shortcodes.totals()).thenReturn(new ShortcodeTotalsDto(5, 4, 1, 0, 4, 1, 3, 2,
                new BigDecimal("2412680.00"), 3, 0));
        when(shortcodes.callbackUrls()).thenReturn(new ShortcodeCallbackUrlsDto("https://x/stk", "https://x/c",
                "https://x/v", "https://x/r", "https://x/t"));
        when(shortcodes.get(ID)).thenReturn(detail());

        mockMvc.perform(get(BASE + "/totals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.c2b_registered").value(2))
                .andExpect(jsonPath("$.credentials_due").doesNotExist());
        mockMvc.perform(get(BASE + "/callback-urls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.c2b_confirmation_url").value("https://x/c"));
        mockMvc.perform(get(BASE + "/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentials.consumer_key_masked").value("••••Qx7d"))
                .andExpect(jsonPath("$.credentials.consumer_key").doesNotExist())
                .andExpect(jsonPath("$.credentials.initiator_password_set").value(false));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void anUnknownShortcodeIs404() throws Exception {
        when(shortcodes.get(ID)).thenThrow(new NotFoundException("Shortcode " + ID + " not found"));
        mockMvc.perform(get(BASE + "/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Shortcode " + ID + " not found"));
    }

    @Test
    void anonymousCallersAreTurnedAway() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().is4xxClientError());
        verifyNoInteractions(shortcodes);
    }

    // ---- create and update (operator)

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorCreatesADraft() throws Exception {
        when(shortcodes.create(any())).thenReturn(detail());

        mockMvc.perform(json(post(BASE), CREATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(ID.toString()))
                .andExpect(jsonPath("$.consumer_secret").doesNotExist());
        ArgumentCaptor<CreateShortcodeRequest> request = ArgumentCaptor.forClass(CreateShortcodeRequest.class);
        verify(shortcodes).create(request.capture());
        assertThat(request.getValue().consumerSecret()).isEqualTo("secret-9KfA");
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anInvalidCreateListsEveryFieldError() throws Exception {
        mockMvc.perform(json(post(BASE), """
                        {"shortcode":"12ab","business_name":"","environment":"SANDBOX","consumer_key":"k",
                         "consumer_secret":"s","min_amount":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'shortcode')].message").value("must be 5 to 10 digits"))
                .andExpect(jsonPath("$.field_errors[?(@.field == 'shortcode_type')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'business_name')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'min_amount')]").exists());
        verifyNoInteractions(shortcodes);
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void aDuplicateShortcodeIs409() throws Exception {
        when(shortcodes.create(any())).thenThrow(new ConflictException("Shortcode 600100 already exists"));
        mockMvc.perform(json(post(BASE), CREATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shortcode 600100 already exists"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerMayNotCreateOrUpdate() throws Exception {
        mockMvc.perform(json(post(BASE), CREATE)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(json(patch(BASE + "/" + ID), "{\"business_name\":\"New\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(shortcodes);
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorUpdatesTheProfileAndBadValuesAreRefused() throws Exception {
        when(shortcodes.update(eq(ID), any())).thenReturn(detail());
        mockMvc.perform(json(patch(BASE + "/" + ID), "{\"business_name\":\"Renamed\",\"max_amount\":250000}"))
                .andExpect(status().isOk());
        mockMvc.perform(json(patch(BASE + "/" + ID), "{\"business_name\":\"  \",\"callback_url\":\"http://plain\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'business_name')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'callback_url')]").exists());
    }

    // ---- credentials, activate, suspend (admin)

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void anAdminUpdatesCredentialsActivatesAndSuspends() throws Exception {
        when(shortcodes.updateCredentials(eq(ID), any())).thenReturn(detail());
        when(shortcodes.activate(ID)).thenReturn(detail());
        when(shortcodes.suspend(ID, "Leaked")).thenReturn(detail());
        when(shortcodes.suspend(eq(ID), isNull())).thenReturn(detail());

        mockMvc.perform(json(put(BASE + "/" + ID + "/credentials"), "{\"consumer_secret\":\"new-secret\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/" + ID + "/activate")).andExpect(status().isOk());
        mockMvc.perform(json(post(BASE + "/" + ID + "/suspend"), "{\"reason\":\"Leaked\"}")).andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/" + ID + "/suspend")).andExpect(status().isOk());
        verify(shortcodes).suspend(ID, "Leaked");
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void aStaleTransitionIs409() throws Exception {
        when(shortcodes.activate(ID)).thenThrow(new ConflictException("Shortcode 600100 is already active"));
        mockMvc.perform(post(BASE + "/" + ID + "/activate")).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = {"mpesa-operator", "mpesa-finance"})
    void credentialsAndLifecycleNeedAdmin() throws Exception {
        mockMvc.perform(json(put(BASE + "/" + ID + "/credentials"), "{\"consumer_secret\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + ID + "/activate")).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + ID + "/suspend")).andExpect(status().isForbidden());
        verifyNoInteractions(shortcodes);
    }

    // ---- connection test, C2B (operator)

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void aFailedConnectionTestStill200sWithTheReason() throws Exception {
        when(shortcodes.testConnection(ID)).thenReturn(new ConnectionTestResultDto(false, 48,
                "Daraja refused these credentials (400 BAD_REQUEST)", LocalDateTime.parse("2026-10-27T09:00:00")));
        mockMvc.perform(post(BASE + "/" + ID + "/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.latency_ms").value(48))
                .andExpect(jsonPath("$.error").value("Daraja refused these credentials (400 BAD_REQUEST)"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerMayNotRunChecksOrRegister() throws Exception {
        mockMvc.perform(post(BASE + "/" + ID + "/test-connection")).andExpect(status().isForbidden());
        mockMvc.perform(json(post(BASE + "/" + ID + "/c2b-registrations"), "{\"response_type\":\"Completed\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post(BASE + "/" + ID + "/simulate-c2b"), "{\"phone_number\":\"254708374149\",\"amount\":10}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(shortcodes, c2b);
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void registeringC2bUrlsAnswers201OrTheUpstreamFailure() throws Exception {
        when(c2b.register(ID, C2bResponseType.Cancelled)).thenReturn(new C2bRegistrationDto(UUID.randomUUID(),
                C2bResponseType.Cancelled, "https://x/c", "https://x/v", true, "0", "Success", "jane.doe",
                LocalDateTime.now()));
        when(c2b.register(ID, C2bResponseType.Completed))
                .thenThrow(UpstreamException.badGateway("Safaricom refused to register the C2B URLs"))
                .thenThrow(UpstreamException.unavailable("Safaricom could not be reached"));

        mockMvc.perform(json(post(BASE + "/" + ID + "/c2b-registrations"), "{\"response_type\":\"Cancelled\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.response_type").value("Cancelled"));
        mockMvc.perform(json(post(BASE + "/" + ID + "/c2b-registrations"), "{\"response_type\":\"Completed\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Safaricom refused to register the C2B URLs"));
        mockMvc.perform(json(post(BASE + "/" + ID + "/c2b-registrations"), "{\"response_type\":\"Completed\"}"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(json(post(BASE + "/" + ID + "/c2b-registrations"), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("response_type"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerReadsTheRegistrationHistoryNewestFirst() throws Exception {
        when(c2b.history(eq(ID), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        mockMvc.perform(get(BASE + "/" + ID + "/c2b-registrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(c2b).history(eq(ID), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void simulationValidatesItsInputAndAnswers409OffSandbox() throws Exception {
        when(c2b.simulate(eq(ID), any())).thenReturn(new SimulateC2bResultDto("AG_1", "orig", "Accepted"))
                .thenThrow(new ConflictException("C2B payments can only be simulated on a sandbox shortcode"));

        mockMvc.perform(json(post(BASE + "/" + ID + "/simulate-c2b"),
                        "{\"phone_number\":\"254708374149\",\"amount\":10,\"bill_ref_number\":\"ELM-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id").value("AG_1"));
        mockMvc.perform(json(post(BASE + "/" + ID + "/simulate-c2b"), "{\"phone_number\":\"254708374149\",\"amount\":10}"))
                .andExpect(status().isConflict());
        mockMvc.perform(json(post(BASE + "/" + ID + "/simulate-c2b"), "{\"phone_number\":\"0712\",\"amount\":10.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'phone_number')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'amount')]").exists());
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void aTestStkDefaultsToOneShillingAndRefusesMoreThanTen() throws Exception {
        when(c2b.testStk(eq(ID), any())).thenReturn(new TestStkResultDto("ws_CO_1", "m-1", "Check your phone"));

        mockMvc.perform(json(post(BASE + "/" + ID + "/test-stk"), "{\"phone_number\":\"254708374149\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkout_request_id").value("ws_CO_1"));
        mockMvc.perform(json(post(BASE + "/" + ID + "/test-stk"), "{\"phone_number\":\"254708374149\",\"amount\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("amount"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerCannotSendATestStk() throws Exception {
        mockMvc.perform(json(post(BASE + "/" + ID + "/test-stk"), "{\"phone_number\":\"254708374149\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(c2b);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static ShortcodeHealthDto health() {
        return new ShortcodeHealthDto(true, LocalDateTime.now(), 312, null, C2bRegistrationState.REGISTERED,
                LocalDateTime.now(), LocalDateTime.now(), new BigDecimal("902140.00"), new BigDecimal("1640210.00"),
                BigDecimal.ZERO, LocalDateTime.now(), BigDecimal.ZERO, null);
    }

    private static ShortcodeSummaryDto summary() {
        return new ShortcodeSummaryDto(ID, "600100", "Elimika Fees", ShortcodeType.PAYBILL, Environment.PRODUCTION,
                ShortcodeStatus.ACTIVE, true, LocalDateTime.now(), LocalDateTime.now(), health(),
                List.of(new ShortcodeSummaryDto.ProductRef("elimika-backend", "Elimika")));
    }

    private static ShortcodeDetailDto detail() {
        return new ShortcodeDetailDto(ID, "600100", "Elimika Fees", ShortcodeType.PAYBILL, Environment.SANDBOX,
                ShortcodeStatus.DRAFT, false, "ELIMIKA", "Elimika fees", BigDecimal.ONE, new BigDecimal("150000"),
                "https://x/stk", null, null, null, null, null, null, LocalDateTime.now(), LocalDateTime.now(),
                "jane.doe", "jane.doe", new ShortcodeCredentialsDto("••••Qx7d", "••••9KfA", null, null, false, false,
                LocalDateTime.now()), health());
    }
}
