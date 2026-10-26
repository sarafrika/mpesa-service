package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Only the fields given change. */
public record UpdateWebhookSubscriptionRequest(
        @Schema(example = "https://api.elimika.sarafrika.com/hooks/mpesa")
        @Size(max = 500)
        @JsonProperty("url") String url,
        @JsonProperty("event_types") List<String> eventTypes,
        @JsonProperty("active") Boolean active) {
}
