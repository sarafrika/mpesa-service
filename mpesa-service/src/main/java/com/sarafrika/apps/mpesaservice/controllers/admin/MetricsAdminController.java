package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.LatestTransactionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.MetricsSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.MetricsTimeseriesDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.PaybillHealthDto;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsQueries.Filter;
import com.sarafrika.apps.mpesaservice.services.metrics.MetricsService;
import com.sarafrika.apps.mpesaservice.services.metrics.PaybillHealthService;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionScope;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('mpesa-viewer')")
@Tag(name = "Admin: Metrics", description = "Figures for the console Overview")
@ApiResponse(responseCode = "400", description = "Invalid parameters",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class MetricsAdminController {

    private static final String FROM = "Start of the period, inclusive (ISO-8601 with offset)";
    private static final String TO = "End of the period, exclusive; at most 400 days after from";
    private static final String SHORTCODE = "Only this shortcode";
    private static final String ENVIRONMENT = "Only shortcodes in this environment";

    private final MetricsService metricsService;
    private final PaybillHealthService paybillHealthService;
    private final AttentionService attentionService;

    @GetMapping("/summary")
    @Operation(operationId = "getMetricsSummary",
            summary = "Collected, paid out, success rate and float for a period, with the previous period")
    @ApiResponse(responseCode = "200", description = "Key figures")
    public MetricsSummaryDto getMetricsSummary(
            @Parameter(description = FROM, required = true)
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = TO, required = true)
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Start of the comparison period, which has the same length as from..to and "
                    + "must end by from; defaults to the period right before from (e.g. yesterday 00:00 for today so far)")
            @RequestParam(name = "previous_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime previousFrom,
            @Parameter(description = SHORTCODE)
            @RequestParam(name = "shortcode_uuid", required = false) UUID shortcodeUuid,
            @Parameter(description = ENVIRONMENT)
            @RequestParam(name = "environment", required = false) Environment environment) {
        return metricsService.summary(from.toInstant(), to.toInstant(),
                previousFrom == null ? null : previousFrom.toInstant(), new Filter(shortcodeUuid, environment));
    }

    @GetMapping("/timeseries")
    @Operation(operationId = "getMetricsTimeseries",
            summary = "Successful collections and payouts per hour or day (Africa/Nairobi), zero-filled")
    @ApiResponse(responseCode = "200", description = "One point per bucket, oldest first")
    public MetricsTimeseriesDto getMetricsTimeseries(
            @Parameter(description = FROM, required = true)
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = TO, required = true)
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Bucket size; hour allows at most 31 days",
                    schema = @Schema(type = "string", allowableValues = {"hour", "day"}, defaultValue = "day"))
            @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = SHORTCODE)
            @RequestParam(name = "shortcode_uuid", required = false) UUID shortcodeUuid,
            @Parameter(description = ENVIRONMENT)
            @RequestParam(name = "environment", required = false) Environment environment) {
        return metricsService.timeseries(from.toInstant(), to.toInstant(), bucket,
                new Filter(shortcodeUuid, environment));
    }

    @GetMapping("/paybill-health")
    @Operation(operationId = "listPaybillHealth",
            summary = "Every shortcode with today's traffic, last activity, latest float and state")
    @ApiResponse(responseCode = "200", description = "Active shortcodes first, then by name")
    public List<PaybillHealthDto> listPaybillHealth(
            @Parameter(description = ENVIRONMENT)
            @RequestParam(name = "environment", required = false) Environment environment) {
        return paybillHealthService.health(environment);
    }

    @GetMapping("/attention")
    @Operation(operationId = "listAttentionItems", summary = "What needs someone's attention now, most severe first")
    @ApiResponse(responseCode = "200", description = "At most 50 items")
    public List<AttentionItemDto> listAttentionItems(
            @Parameter(description = SHORTCODE)
            @RequestParam(name = "shortcode_uuid", required = false) UUID shortcodeUuid,
            @Parameter(description = ENVIRONMENT)
            @RequestParam(name = "environment", required = false) Environment environment) {
        return attentionService.items(new AttentionScope(shortcodeUuid, environment));
    }

    @GetMapping("/latest")
    @Operation(operationId = "listLatestTransactions", summary = "The newest payments in and out, newest first")
    @ApiResponse(responseCode = "200", description = "Up to limit payments")
    public List<LatestTransactionDto> listLatestTransactions(
            @Parameter(description = "How many, 1 to 20",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "20",
                            defaultValue = "6"))
            @RequestParam(name = "limit", required = false) Integer limit,
            @Parameter(description = SHORTCODE)
            @RequestParam(name = "shortcode_uuid", required = false) UUID shortcodeUuid,
            @Parameter(description = ENVIRONMENT)
            @RequestParam(name = "environment", required = false) Environment environment) {
        return metricsService.latest(limit, new Filter(shortcodeUuid, environment));
    }
}
