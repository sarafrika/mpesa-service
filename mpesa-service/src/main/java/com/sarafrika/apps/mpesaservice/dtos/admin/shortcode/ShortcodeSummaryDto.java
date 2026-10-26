package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One row of the paybills list. */
@Schema(name = "ShortcodeSummary")
public record ShortcodeSummaryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "600100")
        @JsonProperty("shortcode") String shortcode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika Fees")
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_type") ShortcodeType shortcodeType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("environment") Environment environment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("status") ShortcodeStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("is_active") boolean active,
        @Schema(description = "When the stored credentials were last saved here")
        @JsonProperty("credentials_updated_at") LocalDateTime credentialsUpdatedAt,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("health") ShortcodeHealthDto health,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Sarafrika products with an active grant on this shortcode"))
        @JsonProperty("products") List<ProductRef> products) {

    /** A product (Keycloak client) using this shortcode. */
    @Schema(name = "ShortcodeProductRef")
    public record ProductRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "elimika-backend")
            @JsonProperty("client_id") String clientId,
            @Schema(example = "Elimika")
            @JsonProperty("display_name") String displayName) {
    }
}
