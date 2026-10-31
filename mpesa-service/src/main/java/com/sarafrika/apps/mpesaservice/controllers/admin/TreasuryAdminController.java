package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bDestinationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bSweepSubmittedDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bTransferDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.BalanceReportDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.BalanceSnapshotDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.CreateB2bDestinationRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.RequestB2bSweepRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.ShortcodeFloatStatusDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.UpdateB2bDestinationRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.UpdateFloatThresholdRequest;
import com.sarafrika.apps.mpesaservice.services.treasury.B2bDestinationService;
import com.sarafrika.apps.mpesaservice.services.treasury.B2bSweepService;
import com.sarafrika.apps.mpesaservice.services.treasury.BalanceService;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bTransferStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import com.sarafrika.apps.mpesaservice.dtos.admin.CloseAsFailedRequest;
import com.sarafrika.apps.mpesaservice.services.treasury.B2bTransferService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shortcodes/{uuid}")
@RequiredArgsConstructor
@Tag(name = "Admin: Treasury", description = "Float: Account Balance queries, low-float threshold and B2B sweeps")
@ApiResponse(responseCode = "400", description = "Invalid parameters or body",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Missing the role the operation needs",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "404", description = "No such shortcode (or destination)",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class TreasuryAdminController {

    static final Map<String, String> DESTINATION_SORTABLE = Map.of("name", "name", "created_at", "createdAt");
    static final Sort DESTINATION_DEFAULT_SORT = Sort.by(Sort.Order.asc("name"));
    static final Map<String, String> TRANSFER_SORTABLE = Map.of("created_at", "createdAt", "amount", "amount");
    static final Sort TRANSFER_DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final BalanceService balances;
    private final B2bDestinationService destinations;
    private final B2bSweepService sweeps;
    private final B2bTransferService transfers;

    // ---- balance

    @PostMapping("/balance-query")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "queryShortcodeBalance", summary = "Ask Safaricom for the shortcode's account balances",
            description = "Daraja answers later on the result URL, so this returns the PENDING query; poll "
                    + "getShortcodeBalance until last_query settles. Needs the shortcode's initiator.")
    @ApiResponse(responseCode = "202", description = "The query was accepted by Safaricom and is pending")
    @ApiResponse(responseCode = "409", description = "The shortcode has no initiator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused the query",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Safaricom could not be reached",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public BalanceSnapshotDto queryShortcodeBalance(@Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid) {
        return balances.requestQuery(uuid);
    }

    @GetMapping("/balance")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getShortcodeBalance",
            summary = "Latest balances, the newest query and the low-float threshold")
    @ApiResponse(responseCode = "200", description = "The shortcode's float status")
    public ShortcodeFloatStatusDto getShortcodeBalance(@Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid) {
        return balances.status(uuid);
    }

    @GetMapping("/balance/history")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listShortcodeBalanceHistory",
            summary = "Completed balance results in a time range, oldest first",
            description = "Defaults to the last 7 days; the range may span at most 31 days and returns at most "
                    + "1000 results.")
    @ApiResponse(responseCode = "200", description = "Balance results, oldest first")
    public List<BalanceSnapshotDto> listShortcodeBalanceHistory(
            @Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
            @Parameter(description = "Inclusive start, ISO-8601 with offset; default 7 days before to")
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Exclusive end, ISO-8601 with offset; default now")
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return balances.history(uuid, from, to);
    }

    @GetMapping("/balance/reports")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listShortcodeBalanceReports",
            summary = "Recent balance reports, newest first: balance results, settled payouts and C2B payments",
            description = "Completed Account Balance results, successful B2C payouts that reported the float and "
                    + "successful C2B payments that carried OrgAccountBalance, merged and cut to limit.")
    @ApiResponse(responseCode = "200", description = "Balance reports, newest first")
    public List<BalanceReportDto> listShortcodeBalanceReports(
            @Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
            @Parameter(description = "How many reports to return",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "50",
                            defaultValue = "10"))
            @RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {
        return balances.reports(uuid, limit);
    }

    @PatchMapping("/float-threshold")
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "updateShortcodeFloatThreshold",
            summary = "Set the shortcode's low-float threshold; null goes back to the service default")
    @ApiResponse(responseCode = "200", description = "The float status with the new threshold")
    public ShortcodeFloatStatusDto updateShortcodeFloatThreshold(
            @Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid, @Valid @RequestBody UpdateFloatThresholdRequest request) {
        return balances.updateThreshold(uuid, request.lowFloatThreshold());
    }

    // ---- B2B destinations

    @GetMapping("/b2b-destinations")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listB2bDestinations", summary = "Paybills and tills this shortcode may sweep to")
    @ApiResponse(responseCode = "200", description = "A page of destinations")
    public PageResponse<B2bDestinationDto> listB2bDestinations(
            @Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: name, created_at. Default name,asc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "name,asc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @Parameter(description = "true: active only; false: inactive only")
            @RequestParam(name = "active", required = false) Boolean active) {
        return PageResponse.from(destinations.list(uuid, active,
                PageRequests.of(page, size, sort, DESTINATION_SORTABLE, DESTINATION_DEFAULT_SORT)));
    }

    @PostMapping("/b2b-destinations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "createB2bDestination", summary = "Add a paybill or till this shortcode may sweep to")
    @ApiResponse(responseCode = "201", description = "The new destination")
    @ApiResponse(responseCode = "409", description = "A destination with this name exists",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public B2bDestinationDto createB2bDestination(@Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
                                                  @Valid @RequestBody CreateB2bDestinationRequest request) {
        return destinations.create(uuid, request);
    }

    @PatchMapping("/b2b-destinations/{destination_uuid}")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "updateB2bDestination", summary = "Change or (de)activate a B2B destination",
            description = "Sweeps waiting for approval against the old details are refused when they run.")
    @ApiResponse(responseCode = "200", description = "The updated destination")
    @ApiResponse(responseCode = "409", description = "A destination with this name exists",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public B2bDestinationDto updateB2bDestination(@Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid, @Parameter(description = "B2B destination uuid")
                                                  @PathVariable("destination_uuid") UUID destinationUuid,
                                                  @Valid @RequestBody UpdateB2bDestinationRequest request) {
        return destinations.update(uuid, destinationUuid, request);
    }

    @DeleteMapping("/b2b-destinations/{destination_uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "deleteB2bDestination", summary = "Remove a B2B destination")
    @ApiResponse(responseCode = "204", description = "Removed; transfers already sent keep their record")
    public void deleteB2bDestination(@Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid, @Parameter(description = "B2B destination uuid")
                                                  @PathVariable("destination_uuid") UUID destinationUuid) {
        destinations.delete(uuid, destinationUuid);
    }

    // ---- sweeps and transfers

    @PostMapping("/b2b-sweeps")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "requestB2bSweep", summary = "Ask for a B2B sweep; it waits in Approvals",
            description = "Nothing is sent until a checker (finance or admin, not the maker) approves it. The amount "
                    + "may not exceed the utility balance last reported, when one is known.")
    @ApiResponse(responseCode = "202", description = "Submitted for approval")
    @ApiResponse(responseCode = "409", description = "The shortcode is not active or has no initiator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Approvals are not available",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public B2bSweepSubmittedDto requestB2bSweep(@Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
                                                @Valid @RequestBody RequestB2bSweepRequest request) {
        return sweeps.request(uuid, request);
    }

    @GetMapping("/b2b-transfers")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listB2bTransfers", summary = "Approved sweeps sent to Safaricom, newest first")
    @ApiResponse(responseCode = "200", description = "A page of transfers")
    public PageResponse<B2bTransferDto> listB2bTransfers(
            @Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: created_at, amount. Default created_at,desc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "created_at,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @Parameter(description = "Only transfers in this state; PENDING includes those whose outcome is unknown")
            @RequestParam(name = "status", required = false) B2bTransferStatus status) {
        return PageResponse.from(sweeps.list(uuid, status,
                PageRequests.of(page, size, sort, TRANSFER_SORTABLE, TRANSFER_DEFAULT_SORT)));
    }

    @PostMapping("/b2b-transfers/{transfer_uuid}/close")
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "closeB2bTransferAsFailed",
            summary = "Close a sweep whose outcome stayed unknown as FAILED after checking the statement (audited)",
            description = "Only for a PENDING transfer whose outcome is unknown. Nothing is sent to Safaricom; a "
                    + "result that arrives later still settles it.")
    @ApiResponse(responseCode = "200", description = "The transfer, now FAILED")
    @ApiResponse(responseCode = "409", description = "The transfer is settled or its outcome is known",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public B2bTransferDto closeB2bTransferAsFailed(
            @Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
            @Parameter(description = "B2B transfer uuid") @PathVariable("transfer_uuid") UUID transferUuid,
            @Valid @RequestBody CloseAsFailedRequest request) {
        return sweeps.view(transfers.closeAsFailed(uuid, transferUuid, request.note()));
    }
}
