package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.B2bCheckoutDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.ProductPayoutDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.ProductPayoutSubmittedDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.RequestPochiPayoutRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.RequestTaxRemittanceRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.daraja.StartB2bCheckoutRequest;
import com.sarafrika.apps.mpesaservice.services.daraja.products.B2bCheckoutService;
import com.sarafrika.apps.mpesaservice.services.daraja.products.ProductPayoutService;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bCheckoutStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.ProductPayoutStatus;
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
@Tag(name = "Admin: Daraja products",
        description = "Flagged Daraja products: Tax Remittance, Business to Pochi and B2B Express Checkout")
@ApiResponse(responseCode = "400", description = "Invalid parameters or body",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Missing the role the operation needs",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "404", description = "No such shortcode",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class DarajaProductAdminController {

    static final Map<String, String> SORTABLE = Map.of("created_at", "createdAt", "amount", "amount");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final ProductPayoutService payouts;
    private final B2bCheckoutService checkouts;

    // ---- Tax Remittance

    @PostMapping("/tax-remittances")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "requestTaxRemittance", summary = "Ask to remit tax to KRA; it waits in Approvals",
            description = "Nothing is sent until a checker (finance or admin, not the maker) approves it. Needs the "
                    + "TAX_REMITTANCE feature, an active shortcode with an initiator, and a PRN issued by KRA.")
    @ApiResponse(responseCode = "202", description = "Submitted for approval")
    @ApiResponse(responseCode = "409", description = "The feature is off, or the shortcode is inactive or has no initiator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Approvals are not available",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ProductPayoutSubmittedDto requestTaxRemittance(@PathVariable UUID uuid,
                                                          @Valid @RequestBody RequestTaxRemittanceRequest request) {
        return payouts.requestTax(uuid, request);
    }

    @GetMapping("/tax-remittances")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listTaxRemittances", summary = "Approved tax remittances sent to Safaricom, newest first")
    @ApiResponse(responseCode = "200", description = "A page of tax remittances")
    public PageResponse<ProductPayoutDto> listTaxRemittances(
            @PathVariable UUID uuid,
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
            @RequestParam(name = "status", required = false) ProductPayoutStatus status) {
        return PageResponse.from(payouts.list(uuid, DarajaFeature.TAX_REMITTANCE, status,
                PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    // ---- Business to Pochi

    @PostMapping("/pochi-payouts")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "requestPochiPayout", summary = "Ask to pay a Pochi la Biashara wallet; it waits in Approvals",
            description = "Nothing is sent until a checker (finance or admin, not the maker) approves it. Needs the "
                    + "BUSINESS_TO_POCHI feature and an active shortcode with an initiator. KES 10 to 250,000.")
    @ApiResponse(responseCode = "202", description = "Submitted for approval")
    @ApiResponse(responseCode = "409", description = "The feature is off, or the shortcode is inactive or has no initiator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Approvals are not available",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ProductPayoutSubmittedDto requestPochiPayout(@PathVariable UUID uuid,
                                                        @Valid @RequestBody RequestPochiPayoutRequest request) {
        return payouts.requestPochi(uuid, request);
    }

    @GetMapping("/pochi-payouts")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listPochiPayouts", summary = "Approved Pochi payments sent to Safaricom, newest first",
            description = "Wallet numbers and names are masked unless the caller has mpesa-finance or mpesa-admin.")
    @ApiResponse(responseCode = "200", description = "A page of Pochi payments")
    public PageResponse<ProductPayoutDto> listPochiPayouts(
            @PathVariable UUID uuid,
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
            @RequestParam(name = "status", required = false) ProductPayoutStatus status) {
        return PageResponse.from(payouts.list(uuid, DarajaFeature.BUSINESS_TO_POCHI, status,
                PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    // ---- B2B Express Checkout

    @PostMapping("/b2b-checkouts")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "startB2bExpressCheckout",
            summary = "Push a USSD prompt asking a merchant's till to pay this paybill",
            description = "Audited. Only the till's nominated number gets the prompt; the callback settles it. Needs "
                    + "the B2B_EXPRESS_CHECKOUT feature on an active paybill.")
    @ApiResponse(responseCode = "202", description = "Safaricom took the push (or never answered: outcome_unknown)")
    @ApiResponse(responseCode = "409", description = "The feature is off, or the shortcode is inactive or not a paybill",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused the push",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public B2bCheckoutDto startB2bExpressCheckout(@PathVariable UUID uuid,
                                                  @Valid @RequestBody StartB2bCheckoutRequest request) {
        return checkouts.start(uuid, request);
    }

    @GetMapping("/b2b-checkouts")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listB2bExpressCheckouts", summary = "B2B Express Checkout pushes, newest first")
    @ApiResponse(responseCode = "200", description = "A page of checkouts")
    public PageResponse<B2bCheckoutDto> listB2bExpressCheckouts(
            @PathVariable UUID uuid,
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
            @RequestParam(name = "status", required = false) B2bCheckoutStatus status) {
        return PageResponse.from(checkouts.list(uuid, status,
                PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }
}
