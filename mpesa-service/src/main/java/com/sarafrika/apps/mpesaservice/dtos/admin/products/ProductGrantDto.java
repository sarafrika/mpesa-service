package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One product's access to one paybill or till. */
@Schema(name = "ProductGrant")
public record ProductGrantDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "elimika-backend")
        @JsonProperty("client_id") String clientId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika")
        @JsonProperty("product_name") String productName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(example = "600100", description = "Null if the shortcode has since been deleted")
        @JsonProperty("shortcode") String shortcode,
        @Schema(example = "Elimika Fees")
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("operations") List<GrantOperation> operations,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("active") boolean active,
        @Schema(description = "The product's webhook health, null when it has no webhook")
        @JsonProperty("webhook_health") WebhookHealthDto webhookHealth,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt) {
}
