package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Grants a Keycloak client its paybills and operations, and optionally registers its webhook, in one go. */
public record ConnectProductRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "elimika-backend",
                description = "The product's Keycloak client id, as its tokens carry it in azp")
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "may only contain letters, digits, '.', '_', ':' and '-'")
        @JsonProperty("client_id") String clientId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika")
        @NotBlank
        @Size(max = 255)
        @JsonProperty("display_name") String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "grant at least one paybill")
        @JsonProperty("grants") List<@Valid @NotNull GrantInput> grants,
        @Schema(example = "https://api.elimika.sarafrika.com/hooks/mpesa",
                description = "HTTPS (http only for localhost); its signing secret is returned once")
        @Size(max = 500)
        @JsonProperty("webhook_url") String webhookUrl,
        @Schema(description = "Event types for the webhook; empty sends every event the product is entitled to")
        @JsonProperty("webhook_event_types") List<String> webhookEventTypes) {

    @Schema(name = "ProductGrantInput")
    public record GrantInput(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty(message = "choose at least one operation")
            @JsonProperty("operations") Set<@NotNull GrantOperation> operations) {
    }
}
