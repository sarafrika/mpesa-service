package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One event sent (or being sent) to one webhook; payload is filled only when a single delivery is fetched. */
@Schema(name = "WebhookDelivery")
public record WebhookDeliveryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Also the X-Sarafrika-Delivery header")
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("subscription_uuid") UUID subscriptionUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "elimika-backend")
        @JsonProperty("client_id") String clientId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika")
        @JsonProperty("product_name") String productName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("event_id") UUID eventId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "c2b.confirmed")
        @JsonProperty("event_type") String eventType,
        @Schema(example = "TJQ4K2L9XA")
        @JsonProperty("reference") String reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1", description = "Attempts made so far")
        @JsonProperty("attempt") int attempt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("status") WebhookDeliveryStatus status,
        @Schema(example = "200")
        @JsonProperty("response_code") Integer responseCode,
        @Schema(example = "184")
        @JsonProperty("latency_ms") Long latencyMs,
        @JsonProperty("next_attempt_at") Instant nextAttemptAt,
        @Schema(example = "Timed out waiting for the receiver")
        @JsonProperty("error") String error,
        @JsonProperty("delivered_at") Instant deliveredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") Instant createdAt,
        @Schema(implementation = Object.class, type = "object", nullable = true,
                additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
                description = "The exact JSON body that is signed and sent")
        @JsonProperty("payload") JsonNode payload) {
}
