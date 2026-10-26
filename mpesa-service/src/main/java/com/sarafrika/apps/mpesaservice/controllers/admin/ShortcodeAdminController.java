package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ConnectionTestResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.CreateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.RegisterC2bUrlsRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.RotateShortcodeCredentialsRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeCallbackUrlsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeStatus;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeTotalsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SimulateC2bRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SimulateC2bResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SuspendShortcodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.TestStkRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.TestStkResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.UpdateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeAdminService;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeC2bService;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shortcodes")
@RequiredArgsConstructor
@Tag(name = "Admin: Shortcodes", description = "Paybills, tills and B2C shortcodes: onboarding, health and lifecycle")
@ApiResponse(responseCode = "400", description = "Invalid parameters or body",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Missing the role the operation needs",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class ShortcodeAdminController {

    static final Map<String, String> SORTABLE = Map.of(
            "business_name", "businessName", "shortcode", "shortcode", "created_at", "createdAt");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("businessName"));
    static final Map<String, String> REGISTRATION_SORTABLE = Map.of("created_at", "createdAt");
    static final Sort REGISTRATION_DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final ShortcodeAdminService shortcodes;
    private final ShortcodeC2bService c2b;

    @GetMapping
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listShortcodes", summary = "List shortcodes with their health, A to Z by default")
    @ApiResponse(responseCode = "200", description = "A page of shortcodes")
    public PageResponse<ShortcodeSummaryDto> listShortcodes(
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: business_name, shortcode, created_at. "
                    + "Default business_name,asc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "business_name,asc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @RequestParam(name = "type", required = false) ShortcodeType type,
            @RequestParam(name = "environment", required = false) Environment environment,
            @Parameter(description = "true: active only; false: drafts and suspended")
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "status", required = false) ShortcodeStatus status,
            @Parameter(description = "Matches business name (any case) or shortcode")
            @RequestParam(name = "q", required = false) String q) {
        return PageResponse.from(shortcodes.list(
                new ShortcodeAdminService.ShortcodeQuery(type, environment, active, status, q),
                PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @GetMapping("/totals")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getShortcodeTotals", summary = "Counts and float across every shortcode")
    @ApiResponse(responseCode = "200", description = "Totals across every shortcode")
    public ShortcodeTotalsDto getShortcodeTotals() {
        return shortcodes.totals();
    }

    @GetMapping("/callback-urls")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getShortcodeCallbackUrls",
            summary = "The callback URLs this service registers with Safaricom")
    @ApiResponse(responseCode = "200", description = "The configured callback URLs")
    public ShortcodeCallbackUrlsDto getShortcodeCallbackUrls() {
        return shortcodes.callbackUrls();
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getShortcode", summary = "A shortcode's profile, masked credentials and health")
    @ApiResponse(responseCode = "200", description = "The shortcode")
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShortcodeDetailDto getShortcode(@PathVariable UUID uuid) {
        return shortcodes.get(uuid);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "createShortcode", summary = "Add a shortcode as a draft",
            description = "Secrets are write-only. A PRODUCTION shortcode needs mpesa-admin.")
    @ApiResponse(responseCode = "201", description = "The new draft shortcode")
    @ApiResponse(responseCode = "409", description = "The shortcode number already exists",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShortcodeDetailDto createShortcode(@Valid @RequestBody CreateShortcodeRequest request) {
        return shortcodes.create(request);
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "updateShortcode", summary = "Change a shortcode's profile, limits or defaults")
    @ApiResponse(responseCode = "200", description = "The updated shortcode")
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShortcodeDetailDto updateShortcode(@PathVariable UUID uuid,
                                              @Valid @RequestBody UpdateShortcodeRequest request) {
        return shortcodes.update(uuid, request);
    }

    @PutMapping("/{uuid}/credentials")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "rotateShortcodeCredentials", summary = "Rotate a shortcode's Daraja credentials",
            description = "Empty fields keep the stored value. The token check must pass again afterwards.")
    @ApiResponse(responseCode = "200", description = "The shortcode with its new masked credentials")
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShortcodeDetailDto rotateShortcodeCredentials(@PathVariable UUID uuid,
                                                         @Valid @RequestBody RotateShortcodeCredentialsRequest request) {
        return shortcodes.rotateCredentials(uuid, request);
    }

    @PostMapping("/{uuid}/test-connection")
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "testShortcodeConnection",
            summary = "Ask Daraja for an OAuth token with this shortcode's credentials",
            description = "Always 200 with ok=false and the reason when Daraja refuses or cannot be reached.")
    @ApiResponse(responseCode = "200", description = "The outcome of the token check")
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ConnectionTestResultDto testShortcodeConnection(@PathVariable UUID uuid) {
        return shortcodes.testConnection(uuid);
    }

    @PostMapping("/{uuid}/activate")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "activateShortcode", summary = "Let the shortcode take STK pushes and payouts")
    @ApiResponse(responseCode = "200", description = "The active shortcode")
    @ApiResponse(responseCode = "409", description = "Already active, or a draft whose token check has not passed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShortcodeDetailDto activateShortcode(@PathVariable UUID uuid) {
        return shortcodes.activate(uuid);
    }

    @PostMapping("/{uuid}/suspend")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "suspendShortcode", summary = "Refuse STK pushes and payouts on the shortcode")
    @ApiResponse(responseCode = "200", description = "The suspended shortcode")
    @ApiResponse(responseCode = "409", description = "The shortcode is not active",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShortcodeDetailDto suspendShortcode(@PathVariable UUID uuid,
                                               @Valid @RequestBody(required = false) SuspendShortcodeRequest request) {
        return shortcodes.suspend(uuid, request != null ? request.reason() : null);
    }

    @GetMapping("/{uuid}/c2b-registrations")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listShortcodeC2bRegistrations", summary = "C2B URL registration attempts, newest first")
    @ApiResponse(responseCode = "200", description = "A page of registration attempts")
    public PageResponse<C2bRegistrationDto> listShortcodeC2bRegistrations(
            @PathVariable UUID uuid,
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc. Fields: created_at. Default created_at,desc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "created_at,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort) {
        return PageResponse.from(c2b.history(uuid,
                PageRequests.of(page, size, sort, REGISTRATION_SORTABLE, REGISTRATION_DEFAULT_SORT)));
    }

    @PostMapping("/{uuid}/c2b-registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "registerShortcodeC2bUrls", summary = "Register the C2B URLs with Safaricom",
            description = "The attempt is kept either way; a refusal answers 502 and an unreachable Safaricom 503.")
    @ApiResponse(responseCode = "201", description = "Safaricom accepted the URLs")
    @ApiResponse(responseCode = "409", description = "A B2C shortcode has no C2B URLs",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused the registration",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Safaricom could not be reached",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public C2bRegistrationDto registerShortcodeC2bUrls(@PathVariable UUID uuid,
                                                       @Valid @RequestBody RegisterC2bUrlsRequest request) {
        return c2b.register(uuid, request.responseType());
    }

    @PostMapping("/{uuid}/simulate-c2b")
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "simulateShortcodeC2b", summary = "Simulate a C2B payment (sandbox shortcodes only)")
    @ApiResponse(responseCode = "200", description = "Safaricom accepted the simulation")
    @ApiResponse(responseCode = "409", description = "Not a sandbox shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused the simulation",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Safaricom could not be reached",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public SimulateC2bResultDto simulateShortcodeC2b(@PathVariable UUID uuid,
                                                     @Valid @RequestBody SimulateC2bRequest request) {
        return c2b.simulate(uuid, request);
    }

    @PostMapping("/{uuid}/test-stk")
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "sendShortcodeTestStk", summary = "Send a test STK push (KES 1 by default, at most 10)",
            description = "Calls Daraja directly, so no product grant is needed. Only for ACTIVE paybills and tills.")
    @ApiResponse(responseCode = "200", description = "Safaricom sent the prompt to the phone")
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Not active, or a B2C shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused the push",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Safaricom could not be reached",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TestStkResultDto sendShortcodeTestStk(@PathVariable UUID uuid, @Valid @RequestBody TestStkRequest request) {
        return c2b.testStk(uuid, request);
    }
}
