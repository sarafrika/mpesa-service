package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.features.ShortcodeFeatureDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.features.UpdateShortcodeFeatureRequest;
import com.sarafrika.apps.mpesaservice.services.features.FeatureAdminService;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shortcodes/{uuid}/features")
@RequiredArgsConstructor
@Tag(name = "Admin: Features", description = "Per-shortcode switches for Daraja products Safaricom enables separately")
@ApiResponse(responseCode = "400", description = "Invalid parameters or body",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Missing the role the operation needs",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "404", description = "No such shortcode",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class FeatureAdminController {

    private final FeatureAdminService features;

    @GetMapping
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listShortcodeFeatures", summary = "Every flagged Daraja product and whether it is on",
            description = "All products are listed in a fixed order; one never switched on reads enabled false.")
    @ApiResponse(responseCode = "200", description = "The shortcode's feature switches")
    public List<ShortcodeFeatureDto> listShortcodeFeatures(@PathVariable UUID uuid) {
        return features.list(uuid);
    }

    @PutMapping("/{feature}")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "updateShortcodeFeature", summary = "Switch a flagged Daraja product on or off",
            description = "Audited. Switch a product on only after Safaricom has enabled it on the shortcode's app.")
    @ApiResponse(responseCode = "200", description = "The feature as it now stands")
    @ApiResponse(responseCode = "409", description = "RATIBA cannot be switched on: Safaricom's API details are "
            + "unconfirmed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShortcodeFeatureDto updateShortcodeFeature(@PathVariable UUID uuid, @PathVariable DarajaFeature feature,
                                                      @Valid @RequestBody UpdateShortcodeFeatureRequest request) {
        return features.update(uuid, feature, request.enabled());
    }
}
