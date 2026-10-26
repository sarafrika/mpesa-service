package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.FloatSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.LatestTransactionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.MetricsSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.MetricsTimeseriesDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PaybillHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PaybillState;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PeriodMetricsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.TimeseriesPointDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.TypeTotalDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.Filter;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsService;
import com.sarafrika.apps.mpesaservice.services.metrics.PaybillHealthService;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionScope;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MetricsAdminController.class)
@Import(CallbackSecurityConfig.class)
class MetricsAdminControllerTest {

    private static final Instant FROM = Instant.parse("2026-10-19T21:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-26T21:00:00Z");
    private static final UUID SHORTCODE = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetricsService metricsService;
    @MockitoBean
    private PaybillHealthService paybillHealthService;
    @MockitoBean
    private AttentionService attentionService;

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerReadsTheSummaryForAPeriodWithOffsets() throws Exception {
        PeriodMetricsDto current = new PeriodMetricsDto(new BigDecimal("1500.00"), 2,
                List.of(new TypeTotalDto("C2B", new BigDecimal("1000.00"), 1)), new BigDecimal("300.00"), 1,
                5, 3, 1, 1, 1, 0.6);
        when(metricsService.summary(FROM, TO, new Filter(SHORTCODE, Environment.PRODUCTION)))
                .thenReturn(new MetricsSummaryDto(FROM, TO, FROM.minusSeconds(604800), FROM, current, current,
                        new FloatSummaryDto(null, 0, null)));

        mockMvc.perform(get("/api/v1/admin/metrics/summary")
                        .param("from", "2026-10-20T00:00:00+03:00").param("to", "2026-10-26T21:00:00Z")
                        .param("shortcode_uuid", SHORTCODE.toString()).param("environment", "PRODUCTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-19T21:00:00Z"))
                .andExpect(jsonPath("$.current.collected_amount").value(1500.00))
                .andExpect(jsonPath("$.current.collected_by_type[0].payment_type").value("C2B"))
                .andExpect(jsonPath("$.current.success_rate").value(0.6))
                .andExpect(jsonPath("$.previous.payouts_count").value(1))
                .andExpect(jsonPath("$.float.total_amount").isEmpty())
                .andExpect(jsonPath("$.float.shortcodes_reporting").value(0));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void theTimeseriesNamesItsBucketAndZone() throws Exception {
        when(metricsService.timeseries(FROM, TO, "hour", Filter.ALL)).thenReturn(new MetricsTimeseriesDto(FROM, TO,
                "hour", "Africa/Nairobi", List.of(new TimeseriesPointDto(FROM, BigDecimal.TEN, 1, BigDecimal.ZERO, 0))));

        mockMvc.perform(get("/api/v1/admin/metrics/timeseries")
                        .param("from", FROM.toString()).param("to", TO.toString()).param("bucket", "hour"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("hour"))
                .andExpect(jsonPath("$.time_zone").value("Africa/Nairobi"))
                .andExpect(jsonPath("$.points[0].bucket_start").value("2026-10-19T21:00:00Z"))
                .andExpect(jsonPath("$.points[0].collections_count").value(1));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void operatorsSeePaybillHealthToo() throws Exception {
        when(paybillHealthService.health(isNull())).thenReturn(List.of(new PaybillHealthDto(SHORTCODE, "600100",
                "Elimika Fees", "PAYBILL", "PRODUCTION", true, PaybillState.LOW_FLOAT, BigDecimal.TEN, 1,
                BigDecimal.ZERO, 0, Instant.parse("2026-10-26T08:00:00Z"), new BigDecimal("38420.00"), null, 0, 4)));

        mockMvc.perform(get("/api/v1/admin/metrics/paybill-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("LOW_FLOAT"))
                .andExpect(jsonPath("$[0].float_amount").value(38420.00))
                .andExpect(jsonPath("$[0].last_activity_at").value("2026-10-26T08:00:00Z"));
    }

    @Test
    @WithMockUser(roles = "mpesa-finance")
    void attentionItemsAreScopedByTheFilters() throws Exception {
        when(attentionService.items(new AttentionScope(SHORTCODE, Environment.SANDBOX))).thenReturn(List.of(
                new AttentionItemDto("STK_PENDING", AttentionItemDto.INFO, "2 STK requests pending over 5 minutes",
                        "", "/transactions", null)));

        mockMvc.perform(get("/api/v1/admin/metrics/attention")
                        .param("shortcode_uuid", SHORTCODE.toString()).param("environment", "SANDBOX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("STK_PENDING"))
                .andExpect(jsonPath("$[0].level").value("info"))
                .andExpect(jsonPath("$[0].href").value("/transactions"));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void latestTransactionsTakeALimit() throws Exception {
        when(metricsService.latest(eq(3), any())).thenReturn(List.of(new LatestTransactionDto(UUID.randomUUID(),
                "OUT", "B2C", "SUCCESS", new BigDecimal("2000.00"), "TJQ4K2L8WB", null, SHORTCODE, "600200",
                "Elimika Payouts", Instant.parse("2026-10-26T11:31:55Z"))));

        mockMvc.perform(get("/api/v1/admin/metrics/latest").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("OUT"))
                .andExpect(jsonPath("$[0].reference").value("TJQ4K2L8WB"));
        verify(metricsService).latest(3, Filter.ALL);
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void missingOrMalformedParametersAre400s() throws Exception {
        mockMvc.perform(get("/api/v1/admin/metrics/summary").param("to", TO.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("from"));
        mockMvc.perform(get("/api/v1/admin/metrics/summary")
                        .param("from", "yesterday").param("to", TO.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("from"));
        mockMvc.perform(get("/api/v1/admin/metrics/paybill-health").param("environment", "STAGING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("environment"));
        verifyNoInteractions(metricsService, paybillHealthService);
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aRangeTheServiceRefusesIsA400WithItsField() throws Exception {
        when(metricsService.summary(any(), any(), any()))
                .thenThrow(new InvalidRequestException("to", "must be after from"));

        mockMvc.perform(get("/api/v1/admin/metrics/summary").param("from", TO.toString()).param("to", FROM.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("to"))
                .andExpect(jsonPath("$.field_errors[0].message").value("must be after from"));
    }

    @Test
    @WithMockUser(roles = "some-other-role")
    void usersWithoutAConsoleRoleAreRefused() throws Exception {
        for (String path : List.of("summary?from=2026-10-19T21:00:00Z&to=2026-10-26T21:00:00Z",
                "timeseries?from=2026-10-19T21:00:00Z&to=2026-10-26T21:00:00Z", "paybill-health", "attention",
                "latest")) {
            mockMvc.perform(get("/api/v1/admin/metrics/" + path))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
        verifyNoInteractions(metricsService, paybillHealthService, attentionService);
    }

    @Test
    void anonymousCallersAreTurnedAway() throws Exception {
        mockMvc.perform(get("/api/v1/admin/metrics/paybill-health")).andExpect(status().is4xxClientError());
        verifyNoInteractions(paybillHealthService);
    }
}
