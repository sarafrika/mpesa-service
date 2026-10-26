package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.PullReconciliationRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.PullTransactionsStatusDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationExceptionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationRunDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.RegisterPullTransactionsRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ResolveReconciliationExceptionRequest;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.services.reconciliation.PullTransactionsService;
import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationResolver;
import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationService;
import com.sarafrika.apps.mpesaservice.services.reconciliation.StatementParseException;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionKind;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationTrigger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/admin/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Admin: Reconciliation",
        description = "Our ledger against Safaricom's organisation statement or Pull Transactions, matched on the receipt")
public class ReconciliationAdminController {

    static final Map<String, String> RUN_SORTS = Map.of("created_at", "createdAt", "period_start", "periodStart");
    static final Map<String, String> EXCEPTION_SORTS = Map.of("receipt", "receipt", "statement_amount",
            "statementAmount", "created_at", "createdAt");
    static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final ReconciliationService service;
    private final ReconciliationResolver resolver;
    private final PullTransactionsService pull;

    @PostMapping(path = "/runs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "uploadReconciliationStatement",
            summary = "Reconcile a paybill against an M-Pesa organisation statement CSV for whole days")
    @ApiResponse(responseCode = "201", description = "The completed run")
    @ApiResponse(responseCode = "400", description = "Not a statement, unreadable lines (field_errors name each "
            + "line), too large, or a bad period", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such paybill",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ReconciliationRunDto uploadReconciliationStatement(
            @Parameter(description = "The statement exported as CSV from the M-Pesa org portal")
            @RequestPart("file") MultipartFile file,
            @Parameter(schema = @Schema(type = "string", format = "uuid"))
            @RequestPart("shortcode_uuid") String shortcodeUuid,
            @Parameter(description = "First day, East Africa Time", schema = @Schema(type = "string", format = "date"))
            @RequestPart("from") String from,
            @Parameter(description = "Last day (inclusive)", schema = @Schema(type = "string", format = "date"))
            @RequestPart("to") String to) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("file", "the file could not be read");
        }
        return service.reconcileStatement(uuid("shortcode_uuid", shortcodeUuid), date("from", from), date("to", to),
                file.getOriginalFilename(), content);
    }

    @PostMapping("/runs/pull")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "runPullTransactionsReconciliation",
            summary = "Reconcile a paybill's deposits against Safaricom's Pull Transactions (last 48 hours only)")
    @ApiResponse(responseCode = "201", description = "The completed run")
    @ApiResponse(responseCode = "400", description = "Bad period", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Pull Transactions is off for this paybill, or not registered",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused the query",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Safaricom could not be reached",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ReconciliationRunDto runPullTransactionsReconciliation(@Valid @RequestBody PullReconciliationRequest body) {
        return service.reconcilePull(body.shortcodeUuid(),
                body.from().atZoneSameInstant(NAIROBI).toLocalDateTime(),
                body.to().atZoneSameInstant(NAIROBI).toLocalDateTime(), ReconciliationTrigger.MANUAL);
    }

    @GetMapping("/runs")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listReconciliationRuns", summary = "Reconciliation runs, newest first")
    @ApiResponse(responseCode = "200", description = "A page of runs")
    @ApiResponse(responseCode = "400", description = "Bad paging or sort",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<ReconciliationRunDto> listReconciliationRuns(
            @Parameter(description = "Only this paybill's runs") @RequestParam(name = "shortcode_uuid", required = false)
            UUID shortcodeUuid,
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; fields: created_at, period_start. Default created_at,desc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "created_at,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort) {
        return PageResponse.from(service.listRuns(shortcodeUuid,
                PageRequests.of(page, size, sort, RUN_SORTS, Sort.by(Sort.Order.desc("createdAt")))));
    }

    @GetMapping("/runs/{uuid}")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getReconciliationRun", summary = "One reconciliation run")
    @ApiResponse(responseCode = "200", description = "The run")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such run",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ReconciliationRunDto getReconciliationRun(@PathVariable("uuid") UUID uuid) {
        return service.getRun(uuid);
    }

    @GetMapping("/runs/{uuid}/summary")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getReconciliationRunSummary", summary = "A run with its exceptions counted per kind")
    @ApiResponse(responseCode = "200", description = "The summary")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such run",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ReconciliationSummaryDto getReconciliationRunSummary(@PathVariable("uuid") UUID uuid) {
        return service.summary(uuid);
    }

    @GetMapping("/runs/{uuid}/exceptions")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listReconciliationExceptions", summary = "A run's exceptions, filtered by kind and status")
    @ApiResponse(responseCode = "200", description = "A page of exceptions")
    @ApiResponse(responseCode = "400", description = "Bad paging, sort or filter",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such run",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<ReconciliationExceptionDto> listReconciliationExceptions(
            @PathVariable("uuid") UUID uuid,
            @RequestParam(name = "kind", required = false) ReconciliationExceptionKind kind,
            @RequestParam(name = "status", required = false) ReconciliationExceptionStatus status,
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; fields: receipt, statement_amount, created_at. Default newest first",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "statement_amount,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort) {
        return PageResponse.from(service.listExceptions(uuid, kind, status,
                PageRequests.of(page, size, sort, EXCEPTION_SORTS, Sort.by(Sort.Order.desc("id")))));
    }

    @PostMapping("/exceptions/{uuid}/backfill")
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "backfillReconciliationException",
            summary = "Add a deposit missing from our ledger as a C2B payment and tell the product (c2b.confirmed)")
    @ApiResponse(responseCode = "200", description = "The resolved exception; a second call adds nothing")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such exception",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Not a missing deposit, reversed at Safaricom, or already resolved",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ReconciliationExceptionDto backfillReconciliationException(@PathVariable("uuid") UUID uuid) {
        return resolver.backfill(uuid);
    }

    @PostMapping("/exceptions/{uuid}/query-status")
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "queryReconciliationExceptionStatus",
            summary = "Ask Safaricom for our transaction's status; the exception stays open")
    @ApiResponse(responseCode = "200", description = "The exception with the status query it sent")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such exception",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Nothing to query, no initiator, or already resolved",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused the query",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Safaricom could not be reached",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ReconciliationExceptionDto queryReconciliationExceptionStatus(@PathVariable("uuid") UUID uuid) {
        return resolver.queryStatus(uuid);
    }

    @PostMapping("/exceptions/{uuid}/merge")
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "mergeReconciliationDuplicate",
            summary = "Keep the first row of a duplicated receipt and soft-delete the others; amounts never change")
    @ApiResponse(responseCode = "200", description = "The resolved exception")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such exception",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Not a duplicate, the rows differ, or already resolved",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ReconciliationExceptionDto mergeReconciliationDuplicate(@PathVariable("uuid") UUID uuid) {
        return resolver.mergeDuplicate(uuid);
    }

    @PostMapping("/exceptions/{uuid}/resolve")
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "markReconciliationExceptionResolved", summary = "Close an exception with a note")
    @ApiResponse(responseCode = "200", description = "The resolved exception")
    @ApiResponse(responseCode = "400", description = "No note", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such exception",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Already resolved",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ReconciliationExceptionDto markReconciliationExceptionResolved(
            @PathVariable("uuid") UUID uuid, @Valid @RequestBody ResolveReconciliationExceptionRequest body) {
        return resolver.markResolved(uuid, body.note());
    }

    @GetMapping("/shortcodes/{uuid}/pull-transactions")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getPullTransactionsStatus",
            summary = "Whether a paybill may use Pull Transactions and how its registration went")
    @ApiResponse(responseCode = "200", description = "The status")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such paybill",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PullTransactionsStatusDto getPullTransactionsStatus(@PathVariable("uuid") UUID uuid) {
        return pull.status(uuid);
    }

    @PostMapping("/shortcodes/{uuid}/pull-transactions/register")
    @PreAuthorize("hasRole('mpesa-finance')")
    @Operation(operationId = "registerPullTransactions",
            summary = "Register a paybill for Pull Transactions with Safaricom (one-time)")
    @ApiResponse(responseCode = "200", description = "Safaricom's answer; registered is false if it refused")
    @ApiResponse(responseCode = "400", description = "Bad nominated number",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-finance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such paybill",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Pull Transactions is off for this paybill",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused the request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Safaricom could not be reached",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PullTransactionsStatusDto registerPullTransactions(@PathVariable("uuid") UUID uuid,
                                                              @Valid @RequestBody RegisterPullTransactionsRequest body) {
        return pull.register(uuid, body.nominatedNumber());
    }

    /** "line 12" field errors let the upload dialog list every line that failed. */
    @ExceptionHandler(StatementParseException.class)
    ResponseEntity<ApiError> unreadableStatement(StatementParseException ex) {
        List<ApiError.FieldError> fields = ex.rowErrors().isEmpty()
                ? List.of(new ApiError.FieldError("file", ex.getMessage()))
                : ex.rowErrors().stream().map(e -> new ApiError.FieldError("line " + e.line(), e.message())).toList();
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, ex.getMessage(), fields));
    }

    private static UUID uuid(String field, String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(field, "must be a UUID");
        }
    }

    private static LocalDate date(String field, String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(field, "must be a date like 2026-10-25");
        }
    }
}
