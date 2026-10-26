package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A Sarafrika product: a Keycloak client with grants or machine API traffic, and how it has been doing. */
@Schema(name = "Product")
public record ProductDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "elimika-backend")
        @JsonProperty("client_id") String clientId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika",
                description = "The display name from its grants, else the client id")
        @JsonProperty("display_name") String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("state") State state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("stats") Stats stats,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("grants") List<ProductGrantDto> grants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Every operation in its active grants")
        @JsonProperty("operations") List<GrantOperation> operations,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("webhooks") List<WebhookSubscriptionDto> webhooks) {

    /** LIVE: active grants and traffic in 7 days; IDLE: granted, no traffic; PAUSED: every grant off;
     * UNGRANTED: traffic or a webhook but no grants. */
    @Schema(name = "ProductState")
    public enum State { LIVE, IDLE, PAUSED, UNGRANTED }

    /** The last 7 days. KES in counts successful payments on shortcodes it holds STK_PUSH or C2B_FEED on. */
    @Schema(name = "ProductStats")
    public record Stats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "8900000.00")
            @JsonProperty("kes_moved_7d") BigDecimal kesMoved7d,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "7200000.00")
            @JsonProperty("kes_in_7d") BigDecimal kesIn7d,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1700000.00")
            @JsonProperty("kes_out_7d") BigDecimal kesOut7d,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "41280")
            @JsonProperty("api_calls_7d") long apiCalls7d,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "124")
            @JsonProperty("api_errors_7d") long apiErrors7d,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0.003",
                    description = "Errors over calls, 0 to 1; 0 when there were no calls")
            @JsonProperty("error_rate_7d") double errorRate7d,
            @JsonProperty("last_call_at") Instant lastCallAt) {
    }
}
