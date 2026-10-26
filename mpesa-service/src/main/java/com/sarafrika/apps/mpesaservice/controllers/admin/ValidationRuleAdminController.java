package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.UpdateValidationRulesRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationDryRunRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationDryRunResult;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.ValidationRulesDto;
import com.sarafrika.apps.mpesaservice.services.collections.ValidationRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shortcodes/{uuid}/validation-rules")
@RequiredArgsConstructor
@Tag(name = "Admin: C2B validation", description = "How a paybill answers Safaricom's C2B validation request")
public class ValidationRuleAdminController {

    private final ValidationRuleService ruleService;

    @GetMapping
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getValidationRules", summary = "A shortcode's C2B validation rules")
    @ApiResponse(responseCode = "200", description = "The rules; configured is false when none were saved")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ValidationRulesDto getValidationRules(@PathVariable("uuid") UUID uuid) {
        return ruleService.get(uuid);
    }

    @PutMapping
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "updateValidationRules", summary = "Replace a shortcode's C2B validation rules")
    @ApiResponse(responseCode = "200", description = "The saved rules")
    @ApiResponse(responseCode = "400", description = "A field is invalid, e.g. the pattern does not compile",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-operator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ValidationRulesDto updateValidationRules(@PathVariable("uuid") UUID uuid,
                                                    @Valid @RequestBody UpdateValidationRulesRequest request) {
        return ruleService.update(uuid, request);
    }

    @PostMapping("/dry-run")
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "dryRunValidationRules",
            summary = "Run a pretend payment through the saved rules, or through unsaved ones")
    @ApiResponse(responseCode = "200", description = "What Safaricom would be told")
    @ApiResponse(responseCode = "400", description = "A field is invalid",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-operator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ValidationDryRunResult dryRunValidationRules(@PathVariable("uuid") UUID uuid,
                                                        @Valid @RequestBody ValidationDryRunRequest request) {
        return ruleService.dryRun(uuid, request);
    }
}
