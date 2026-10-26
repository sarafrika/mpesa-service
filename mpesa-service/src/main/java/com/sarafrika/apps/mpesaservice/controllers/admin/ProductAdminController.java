package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ConnectProductRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ConnectProductResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.GrantableShortcodeDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ProductDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ProductGrantDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.UpsertProductGrantRequest;
import com.sarafrika.apps.mpesaservice.services.products.ProductAdminService;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin: Products", description = "Sarafrika products (Keycloak clients) and the paybills and operations "
        + "granted to them")
public class ProductAdminController {

    static final Map<String, String> PRODUCT_SORTS = Map.of(
            "name", "displayName", "client_id", "clientId", "kes_moved_7d", "kesMoved7d",
            "api_calls_7d", "apiCalls7d");
    static final Sort PRODUCT_DEFAULT_SORT = Sort.by(Sort.Order.asc("displayName"));
    static final Map<String, String> GRANT_SORTS = Map.of(
            "created_at", "createdAt", "client_id", "clientId", "display_name", "displayName");
    static final Sort GRANT_DEFAULT_SORT = Sort.by(Sort.Order.asc("displayName"));
    static final Map<String, String> SHORTCODE_SORTS = Map.of("shortcode", "shortcode", "business_name",
            "businessName");
    static final Sort SHORTCODE_DEFAULT_SORT = Sort.by(Sort.Order.asc("shortcode"));

    private final ProductAdminService productService;

    @GetMapping("/products")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listProducts", summary = "List products with their grants, 7-day figures and webhooks")
    @ApiResponse(responseCode = "200", description = "A page of products")
    @ApiResponse(responseCode = "400", description = "Negative page, size below 1 or an unknown sort field",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<ProductDto> listProducts(
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: name, client_id, kes_moved_7d, "
                    + "api_calls_7d. Default name,asc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "name,asc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @Parameter(description = "Matches part of the client id or display name")
            @RequestParam(name = "search", required = false) String search) {
        return PageResponse.from(productService.listProducts(search,
                PageRequests.of(page, size, sort, PRODUCT_SORTS, PRODUCT_DEFAULT_SORT)));
    }

    @GetMapping("/products/{clientId}")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getProduct", summary = "One product by its Keycloak client id")
    @ApiResponse(responseCode = "200", description = "The product")
    @ApiResponse(responseCode = "404", description = "No grants, webhook or traffic for that client",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ProductDto getProduct(@PathVariable String clientId) {
        return productService.getProduct(clientId);
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "connectProduct",
            summary = "Connect a product: grant its paybills and operations and optionally register its webhook",
            description = "The webhook signing secret is in this response only.")
    @ApiResponse(responseCode = "201", description = "Connected")
    @ApiResponse(responseCode = "400", description = "Invalid fields",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "The client already has grants",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<ConnectProductResponse> connectProduct(@Valid @RequestBody ConnectProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.connect(request));
    }

    @PutMapping("/products/{clientId}/grants/{shortcodeUuid}")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "upsertProductGrant", summary = "Grant or change a product's operations on a shortcode")
    @ApiResponse(responseCode = "200", description = "The grant as saved")
    @ApiResponse(responseCode = "400", description = "Invalid fields",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ProductGrantDto upsertProductGrant(@PathVariable String clientId, @PathVariable UUID shortcodeUuid,
                                              @Valid @RequestBody UpsertProductGrantRequest request) {
        return productService.upsertGrant(clientId, shortcodeUuid, request);
    }

    @DeleteMapping("/products/{clientId}/grants/{shortcodeUuid}")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "deleteProductGrant", summary = "Remove a product's access to a shortcode")
    @ApiResponse(responseCode = "204", description = "Removed")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such grant",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> deleteProductGrant(@PathVariable String clientId, @PathVariable UUID shortcodeUuid) {
        productService.deleteGrant(clientId, shortcodeUuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/product-grants")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listProductGrants",
            summary = "List grants, e.g. which products may use one shortcode and for what")
    @ApiResponse(responseCode = "200", description = "A page of grants")
    @ApiResponse(responseCode = "400", description = "Negative page, size below 1 or an unknown sort field",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<ProductGrantDto> listProductGrants(
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: created_at, client_id, display_name. "
                    + "Default display_name,asc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "display_name,asc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @RequestParam(name = "shortcode_uuid", required = false) UUID shortcodeUuid,
            @RequestParam(name = "client_id", required = false) String clientId) {
        return PageResponse.from(productService.listGrants(shortcodeUuid, clientId,
                PageRequests.of(page, size, sort, GRANT_SORTS, GRANT_DEFAULT_SORT)));
    }

    @GetMapping("/product-grants/shortcodes")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listGrantableShortcodes",
            summary = "Paybills and tills a product can be granted, without credentials")
    @ApiResponse(responseCode = "200", description = "A page of shortcodes")
    @ApiResponse(responseCode = "400", description = "Negative page, size below 1 or an unknown sort field",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<GrantableShortcodeDto> listGrantableShortcodes(
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: shortcode, business_name. "
                    + "Default shortcode,asc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "shortcode,asc")))
            @RequestParam(name = "sort", required = false) List<String> sort) {
        return PageResponse.from(productService.listGrantableShortcodes(
                PageRequests.of(page, size, sort, SHORTCODE_SORTS, SHORTCODE_DEFAULT_SORT)));
    }
}
