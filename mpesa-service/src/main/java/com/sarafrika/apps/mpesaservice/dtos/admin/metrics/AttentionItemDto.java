package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Something on the Overview a person should look at, with the console page that deals with it. */
@Schema(name = "AttentionItemDto")
public record AttentionItemDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "LOW_FLOAT",
                description = "What kind of item this is, e.g. LOW_FLOAT, STK_PENDING, PAYBILL_STALE, PAYBILL_FAILING")
        @JsonProperty("kind") String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"critical", "warning", "info"})
        @JsonProperty("level") String level,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika Payouts float below KES 50,000")
        @JsonProperty("title") String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Utility account KES 38,420.00.")
        @JsonProperty("description") String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "/paybills/550e8400-e29b-41d4-a716-446655440000",
                description = "Console path that deals with the item")
        @JsonProperty("href") String href,
        @Schema(nullable = true)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid) {

    public static final String CRITICAL = "critical";
    public static final String WARNING = "warning";
    public static final String INFO = "info";
}
