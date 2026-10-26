package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A product's webhook endpoint. The secret itself is write-only; only its last four characters show. */
@Schema(name = "WebhookSubscription")
public record WebhookSubscriptionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "elimika-backend")
        @JsonProperty("client_id") String clientId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "https://api.elimika.sarafrika.com/hooks/mpesa")
        @JsonProperty("url") String url,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Empty means every event the product is entitled to")
        @JsonProperty("event_types") List<String> eventTypes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("active") boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "••••a91f")
        @JsonProperty("secret_hint") String secretHint,
        @JsonProperty("secret_rotated_at") LocalDateTime secretRotatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("health") WebhookHealthDto health,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt) {
}
