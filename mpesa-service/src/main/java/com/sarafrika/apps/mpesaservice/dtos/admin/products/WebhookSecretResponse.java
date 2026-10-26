package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** The only response that ever carries a webhook's signing secret. */
public record WebhookSecretResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("subscription") WebhookSubscriptionDto subscription,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "whsec_3q2-…",
                description = "Shown once; it cannot be read again, only rotated")
        @JsonProperty("secret") String secret) {
}
