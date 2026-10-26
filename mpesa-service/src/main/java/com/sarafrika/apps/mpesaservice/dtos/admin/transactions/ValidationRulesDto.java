package com.sarafrika.apps.mpesaservice.dtos.admin.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A shortcode's C2B validation rules; configured is false (and everything is accepted) until someone saves them. */
@Schema(name = "ValidationRulesDto")
public record ValidationRulesDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("configured") boolean configured,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("active") boolean active,
        @Schema(description = "Java regex the whole account number must match; null accepts any", example = "^ELM-[0-9]{6}$")
        @JsonProperty("account_pattern") String accountPattern,
        @Schema(example = "10.00")
        @JsonProperty("min_amount") BigDecimal minAmount,
        @Schema(example = "250000.00")
        @JsonProperty("max_amount") BigDecimal maxAmount,
        @Schema(description = "Product endpoint asked to confirm each account", example = "https://elimika.example/mpesa/accounts")
        @JsonProperty("lookup_url") String lookupUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")
        @JsonProperty("lookup_timeout_ms") int lookupTimeoutMs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACCEPT", "REJECT"})
        @JsonProperty("on_lookup_failure") String onLookupFailure,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("updated_by") String updatedBy) {
}
