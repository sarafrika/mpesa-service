package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.ApprovalDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.ApprovalSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.ApproveApprovalRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.BulkPayoutUploadDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PayoutBatchDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PayoutBatchItemDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.RejectApprovalRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.RequestReversalRequest;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.MakerCheckerException;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalService;
import com.sarafrika.apps.mpesaservice.services.approvals.bulk.BulkPayoutService;
import com.sarafrika.apps.mpesaservice.services.approvals.bulk.BulkPayoutViews;
import com.sarafrika.apps.mpesaservice.services.approvals.reversal.ReversalService;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.sarafrika.apps.mpesaservice.dtos.admin.CloseAsFailedRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.BulkPayoutUploadForm;
import com.sarafrika.apps.mpesaservice.services.approvals.reversal.ReversalCallbackService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/approvals")
@RequiredArgsConstructor
@Tag(name = "Admin: Approvals", description = "Maker-checker for reversals, bulk payouts and B2B sweeps")
public class ApprovalAdminController {

    static final Map<String, String> SORTABLE = Map.of("created_at", "createdAt", "amount", "amount",
            "expires_at", "expiresAt");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));
    static final Map<String, String> ITEM_SORTABLE = Map.of("line", "lineNumber", "amount", "amount");
    static final Sort ITEM_DEFAULT_SORT = Sort.by(Sort.Order.asc("lineNumber"));

    private final ApprovalService approvals;
    private final ReversalService reversals;
    private final BulkPayoutService bulkPayouts;
    private final BulkPayoutViews batches;
    private final ReversalCallbackService reversalCallbacks;

    @GetMapping
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listApprovals", summary = "List approval requests, newest first")
    @ApiResponse(responseCode = "200", description = "A page of requests")
    @ApiResponse(responseCode = "400", description = "Bad paging, sort or filter value",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<ApprovalDto> listApprovals(
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: created_at, amount, expires_at. "
                    + "Default created_at,desc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "created_at,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @Parameter(description = "Any of these statuses; repeat or comma-separate",
                    array = @ArraySchema(schema = @Schema(type = "string", allowableValues = {"WAITING", "APPROVED",
                            "REJECTED", "EXPIRED", "EXECUTING", "EXECUTED", "FAILED"})))
            @RequestParam(name = "status", required = false) List<String> status,
            @Parameter(description = "Only requests of this type",
                    schema = @Schema(allowableValues = {"REVERSAL", "BULK_PAYOUT", "B2B_SWEEP",
                            "TAX_REMITTANCE", "BUSINESS_TO_POCHI"}))
            @RequestParam(name = "type", required = false) String type,
            @Parameter(description = "Only requests against this shortcode")
            @RequestParam(name = "shortcode_uuid", required = false) UUID shortcodeUuid) {
        ApprovalService.Filter filter = new ApprovalService.Filter(statuses(status), type, shortcodeUuid);
        return PageResponse.from(approvals.list(filter, PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getApprovalSummary", summary = "How many requests wait for a checker")
    @ApiResponse(responseCode = "200", description = "The counts")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ApprovalSummaryDto getApprovalSummary() {
        return approvals.summary();
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getApproval", summary = "One approval request with its pre-checks and outcome")
    @ApiResponse(responseCode = "200", description = "The request")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ApprovalDto getApproval(
            @Parameter(description = "Approval request uuid") @PathVariable("uuid") UUID uuid) {
        return approvals.get(uuid);
    }

    @PostMapping("/{uuid}/approve")
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "approveApproval",
            summary = "Approve a waiting request; it then runs against Daraja in the background")
    @ApiResponse(responseCode = "200", description = "Approved; status moves on to EXECUTING and EXECUTED")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance, and the checker cannot be the maker",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Already decided, or expired",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ApprovalDto approveApproval(
            @Parameter(description = "Approval request uuid") @PathVariable("uuid") UUID uuid,
                                       @Valid @RequestBody(required = false) ApproveApprovalRequest body) {
        return approvals.approve(uuid, body == null ? null : body.note());
    }

    @PostMapping("/{uuid}/reject")
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "rejectApproval", summary = "Reject a waiting request with a note to the maker")
    @ApiResponse(responseCode = "200", description = "Rejected")
    @ApiResponse(responseCode = "400", description = "The note is missing",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance, and the checker cannot be the maker",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Already decided, or expired",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ApprovalDto rejectApproval(
            @Parameter(description = "Approval request uuid") @PathVariable("uuid") UUID uuid,
                                      @Valid @RequestBody RejectApprovalRequest body) {
        return approvals.reject(uuid, body.note());
    }

    @PostMapping("/reversals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "requestReversal",
            summary = "Ask for a received payment to be reversed; a finance user must approve it")
    @ApiResponse(responseCode = "201", description = "The REVERSAL request, waiting for a checker")
    @ApiResponse(responseCode = "400", description = "Missing reason, or more than the original amount",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-operator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such transaction",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Not a settled incoming payment with a receipt, already "
            + "reversed or being reversed, or its paybill has no initiator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ApprovalDto requestReversal(@Valid @RequestBody RequestReversalRequest body) {
        return approvals.get(reversals.request(body));
    }

    @PostMapping(path = "/bulk-payouts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "uploadBulkPayout", summary = "Check a payout CSV (phone, amount, reference, name); "
            + "without preview its valid rows become a BULK_PAYOUT request waiting for a checker")
    @ApiResponse(responseCode = "200", description = "Every row with its problems, and the totals")
    @ApiResponse(responseCode = "400", description = "Unreadable or oversized file, or no valid rows",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-operator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such paybill",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "The paybill is suspended or has no B2C initiator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = BulkPayoutUploadForm.class)))
    // Every field is a part of the multipart body (documented by BulkPayoutUploadForm), none a query parameter.
    public BulkPayoutUploadDto uploadBulkPayout(
            @Parameter(hidden = true) @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @RequestParam("shortcode_uuid") UUID shortcodeUuid,
            @Parameter(hidden = true) @RequestParam("title") String title,
            @Parameter(hidden = true) @RequestParam(name = "reason", required = false) String reason,
            @Parameter(hidden = true) @RequestParam(name = "preview", required = false, defaultValue = "false")
            boolean preview) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidRequestException("file", "could not be read");
        }
        return bulkPayouts.upload(new BulkPayoutService.Upload(content, file.getOriginalFilename(), shortcodeUuid,
                title, reason, preview));
    }

    @PostMapping("/reversals/{reversal_uuid}/close")
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "closeReversalAsFailed",
            summary = "Close a reversal whose outcome stayed unknown as FAILED after checking the statement (audited)",
            description = "Only for an OUTCOME_UNKNOWN reversal. Nothing is sent to Safaricom. It frees the payment "
                    + "for a new reversal request; a result that arrives later still settles this one.")
    @ApiResponse(responseCode = "200", description = "The REVERSAL request with the reversal now FAILED")
    @ApiResponse(responseCode = "400", description = "The note is missing",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such reversal",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "The reversal's outcome is not unknown",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ApprovalDto closeReversalAsFailed(
            @Parameter(description = "Reversal uuid (ApprovalDto.reversal.uuid)")
            @PathVariable("reversal_uuid") UUID reversalUuid,
            @Valid @RequestBody CloseAsFailedRequest body) {
        return approvals.get(reversalCallbacks.closeAsFailed(reversalUuid, body.note()).getApprovalUuid());
    }

    @GetMapping("/bulk-payouts/{uuid}")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getBulkPayoutBatch", summary = "A bulk payout with its progress and totals")
    @ApiResponse(responseCode = "200", description = "The batch")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such batch",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PayoutBatchDto getBulkPayoutBatch(
            @Parameter(description = "Bulk payout batch uuid") @PathVariable("uuid") UUID uuid) {
        return batches.batch(uuid);
    }

    @GetMapping("/bulk-payouts/{uuid}/items")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listBulkPayoutItems", summary = "The rows of a bulk payout with each one's outcome")
    @ApiResponse(responseCode = "200", description = "A page of rows")
    @ApiResponse(responseCode = "400", description = "Bad paging, sort or outcome",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such batch",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<PayoutBatchItemDto> listBulkPayoutItems(
            @Parameter(description = "Bulk payout batch uuid") @PathVariable("uuid") UUID uuid,
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: line, amount. Default line,asc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "line,asc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @Parameter(schema = @Schema(allowableValues = {"QUEUED", "PENDING", "PAID", "FAILED", "UNKNOWN",
                    "REFUSED", "ERROR"}))
            @RequestParam(name = "outcome", required = false) String outcome) {
        return PageResponse.from(batches.items(uuid, outcome,
                PageRequests.of(page, size, sort, ITEM_SORTABLE, ITEM_DEFAULT_SORT)));
    }

    /** The maker tried to decide their own request: say so instead of a bare "no permission". */
    @ExceptionHandler(MakerCheckerException.class)
    public ResponseEntity<ApiError> makerIsChecker(MakerCheckerException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(HttpStatus.FORBIDDEN, ex.getMessage()));
    }

    private static List<ApprovalStatus> statuses(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().flatMap(v -> java.util.Arrays.stream(v.split(","))).map(String::trim)
                .filter(v -> !v.isEmpty()).map(v -> {
                    try {
                        return ApprovalStatus.valueOf(v.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new InvalidRequestException("status", "unknown status '" + v + "'");
                    }
                }).toList();
    }
}
