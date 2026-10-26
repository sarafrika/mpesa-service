package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.StatusQueryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TransactionDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TransactionDto;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionFeedService;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionStatusService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/transactions")
@RequiredArgsConstructor
@Tag(name = "Admin: Transactions", description = "Every deposit, STK request and payout across all paybills")
public class TransactionAdminController {

    static final Map<String, String> SORTABLE = Map.of("created_at", "createdAt", "amount", "amount");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final TransactionFeedService feedService;
    private final TransactionStatusService statusService;

    @GetMapping
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listTransactions", summary = "List incoming and outgoing payments, newest first")
    @ApiResponse(responseCode = "200", description = "A page of transactions")
    @ApiResponse(responseCode = "400", description = "Bad paging, sort or filter value",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<TransactionDto> listTransactions(
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
            @Parameter(description = "Money in or money out", schema = @Schema(allowableValues = {"in", "out"}))
            @RequestParam(name = "direction", required = false) String direction,
            @Parameter(schema = @Schema(allowableValues = {"C2B", "STK_PUSH", "B2B_EXPRESS_CHECKOUT", "B2C", "B2B"}))
            @RequestParam(name = "type", required = false) String type,
            @Parameter(schema = @Schema(allowableValues = {"PENDING", "SUCCESS", "FAILED", "CANCELLED"}))
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "shortcode_uuid", required = false) UUID shortcodeUuid,
            @Parameter(description = "Payer or recipient phone; 07…, 7… and +254… are all understood")
            @RequestParam(name = "phone", required = false) String phone,
            @Parameter(description = "Receipt, checkout id or conversation id (exact), or account/client reference (prefix)")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Created at or after (ISO-8601 with offset)")
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Created before (ISO-8601 with offset)")
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        TransactionFeedService.Filter filter = new TransactionFeedService.Filter(direction, type, status,
                shortcodeUuid, phone, q, from, to);
        return PageResponse.from(feedService.list(filter, PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getTransaction", summary = "One transaction with its timeline and raw callback")
    @ApiResponse(responseCode = "200", description = "The transaction")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such transaction",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TransactionDetailDto getTransaction(@PathVariable("uuid") UUID uuid) {
        return feedService.detail(uuid);
    }

    @PostMapping("/{uuid}/query-status")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "queryTransactionStatus",
            summary = "Ask Safaricom for the transaction's status; the answer settles it when it arrives")
    @ApiResponse(responseCode = "202", description = "The query was sent; an STK query is already answered")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-operator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such transaction",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Nothing Safaricom could be asked about, or no initiator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused the query",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Safaricom could not be reached",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public StatusQueryDto queryTransactionStatus(@PathVariable("uuid") UUID uuid) {
        return statusService.request(uuid);
    }
}
