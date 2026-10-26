package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.AuditEventDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
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

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "listAuditEvents", summary = "List audit events, newest first")
    @ApiResponse(responseCode = "200", description = "A page of audit events")
    @ApiResponse(responseCode = "400", description = "Invalid paging or filters",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<AuditEventDto> listAuditEvents(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "entity_type", required = false) String entityType,
            @RequestParam(name = "entity_id", required = false) String entityId) {
        return PageResponse.from(auditService.list(
                new AuditService.AuditQuery(actor, action, entityType, entityId), page, size));
    }
}
