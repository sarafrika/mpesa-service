package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateWebhookSubscriptionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "elimika-backend")
        @NotBlank
        @Size(max = 100)
        @JsonProperty("client_id") String clientId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "https://api.elimika.sarafrika.com/hooks/mpesa",
                description = "HTTPS; plain http only for localhost")
        @NotBlank
        @Size(max = 500)
        @JsonProperty("url") String url,
        @Schema(description = "Empty or missing sends every event the product is entitled to")
        @JsonProperty("event_types") List<String> eventTypes) {
}
