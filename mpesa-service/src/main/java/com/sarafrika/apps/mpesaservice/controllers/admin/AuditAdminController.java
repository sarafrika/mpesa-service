package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.AuditEventDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Admin: Audit", description = "The trail of changes made through the console")
public class AuditAdminController {

    static final Map<String, String> SORTABLE = Map.of(
            "created_at", "createdAt", "action", "action", "actor", "actor");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "listAuditEvents", summary = "List audit events, newest first")
    @ApiResponse(responseCode = "200", description = "A page of audit events")
    @ApiResponse(responseCode = "400", description = "Negative page, size below 1 or an unknown sort field",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<AuditEventDto> listAuditEvents(
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: created_at, action, actor. "
                    + "Default created_at,desc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "created_at,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "entity_type", required = false) String entityType,
            @RequestParam(name = "entity_id", required = false) String entityId) {
        return PageResponse.from(auditService.list(new AuditService.AuditQuery(actor, action, entityType, entityId),
                PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }
}
