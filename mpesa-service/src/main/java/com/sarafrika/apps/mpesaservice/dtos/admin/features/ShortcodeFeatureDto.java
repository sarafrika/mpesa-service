package com.sarafrika.apps.mpesaservice.dtos.admin.features;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** One flagged Daraja product on a shortcode; a product never switched reads enabled false with no audit. */
@Schema(name = "ShortcodeFeatureDto")
public record ShortcodeFeatureDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("feature") DarajaFeature feature,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("enabled") boolean enabled,
        @Schema(nullable = true, description = "Who last switched it; null when it was never switched")
        @JsonProperty("updated_by") String updatedBy,
        @Schema(nullable = true)
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "False when the shortcode's type cannot use this product; it cannot be switched on")
        @JsonProperty("available") boolean available) {

    public ShortcodeFeatureDto(DarajaFeature feature, boolean enabled, String updatedBy, OffsetDateTime updatedAt) {
        this(feature, enabled, updatedBy, updatedAt, true);
    }
}
