package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** The connected product; webhook_secret is present only here, once, when a webhook was registered. */
public record ConnectProductResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("product") ProductDto product,
        @JsonProperty("webhook") WebhookSubscriptionDto webhook,
        @Schema(description = "Shown once; store it in the product's secrets now", example = "whsec_3q2-…")
        @JsonProperty("webhook_secret") String webhookSecret) {
}
