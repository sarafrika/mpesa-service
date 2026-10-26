package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackSummaryDto;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackInboxService;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackReplayService;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/callbacks")
@RequiredArgsConstructor
@Tag(name = "Admin: Callbacks", description = "Every request Safaricom sent to a callback URL, and replaying failures")
public class CallbackInboxAdminController {

    static final Map<String, String> SORTABLE = Map.of("received_at", "receivedAt", "attempts", "attempts");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("receivedAt"));

    private final CallbackInboxService inboxService;
    private final CallbackReplayService replayService;

    @GetMapping
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listCallbacks", summary = "List received callbacks, newest first")
    @ApiResponse(responseCode = "200", description = "A page of callbacks")
    @ApiResponse(responseCode = "400", description = "Bad paging, sort or filter value",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<CallbackDto> listCallbacks(
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: received_at, attempts. Default received_at,desc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "received_at,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @Parameter(schema = @Schema(allowableValues = {"RECEIVED", "PROCESSED", "FAILED", "REPLAYED"}))
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Callback kind, e.g. C2B_CONFIRMATION, STK_CALLBACK, B2C_RESULT")
            @RequestParam(name = "kind", required = false) String kind,
            @Parameter(description = "Exact callback path, e.g. /api/v1/callbacks/b2c/result")
            @RequestParam(name = "endpoint", required = false) String endpoint,
            @Parameter(description = "Part of a receipt, TransID, checkout id or conversation id in the body")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Received at or after (ISO-8601 with offset)")
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Received before (ISO-8601 with offset)")
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        CallbackInboxService.Filter filter = new CallbackInboxService.Filter(status, kind, endpoint, q, from, to);
        return PageResponse.from(inboxService.list(filter, PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getCallbackSummary", summary = "Callback counts by status over the last 24 hours")
    @ApiResponse(responseCode = "200", description = "Counts by status")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CallbackSummaryDto getCallbackSummary() {
        return inboxService.summary();
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getCallback",
            summary = "One callback with its headers and body; phones and names masked unless finance or admin")
    @ApiResponse(responseCode = "200", description = "The callback")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such callback",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CallbackDetailDto getCallback(@Parameter(description = "Inbox entry uuid") @PathVariable("uuid") UUID uuid) {
        return inboxService.get(uuid);
    }

    @PostMapping("/{uuid}/replay")
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "replayCallback",
            summary = "Run a failed callback's stored body through its handler again (idempotent; audited)")
    @ApiResponse(responseCode = "200", description = "The callback after the replay; status REPLAYED or FAILED")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-operator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such callback",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Not replayable: handled, in progress, a validation or no body",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CallbackDetailDto replayCallback(@Parameter(description = "Inbox entry uuid") @PathVariable("uuid") UUID uuid) {
        return replayService.replay(uuid);
    }
}
