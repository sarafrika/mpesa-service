package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeCapabilities;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Sarafrika products with an active grant on this shortcode")
        @JsonProperty("products") List<ProductRef> products,
        @Schema(example = "174300", description = "A till's store/HO number (the Go-Live shortcode); null otherwise")
        @JsonProperty("store_number") String storeNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What this shortcode can do: its type's capabilities narrowed to its operations")
        @JsonProperty("capabilities") List<ShortcodeCapability> capabilities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Operations turned on, in enum order")
        @JsonProperty("operations") List<ShortcodeOperation> operations) {

    public ShortcodeSummaryDto(UUID uuid, String shortcode, String businessName, ShortcodeType shortcodeType,
                               Environment environment, ShortcodeStatus status, boolean active,
                               LocalDateTime credentialsUpdatedAt, LocalDateTime createdAt, ShortcodeHealthDto health,
                               List<ProductRef> products) {
        this(uuid, shortcode, businessName, shortcodeType, environment, status, active, credentialsUpdatedAt,
                createdAt, health, products, null, ShortcodeCapabilities.list(shortcodeType),
                List.copyOf(ShortcodeCapabilities.availableOperations(shortcodeType)));
    }

    /** A product (Keycloak client) using this shortcode. */
    @Schema(name = "ShortcodeProductRef")
    public record ProductRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "elimika-backend")
            @JsonProperty("client_id") String clientId,
            @Schema(example = "Elimika")
            @JsonProperty("display_name") String displayName) {
    }
}
