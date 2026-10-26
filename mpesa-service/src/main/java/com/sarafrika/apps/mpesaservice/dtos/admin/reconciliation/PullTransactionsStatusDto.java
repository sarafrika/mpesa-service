package com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Whether a shortcode may use Pull Transactions (its feature switch) and how its registration went. */
@Schema(name = "PullTransactionsStatusDto")
public record PullTransactionsStatusDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The paybill's Pull Transactions feature is switched on")
        @JsonProperty("enabled") boolean enabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("registered") boolean registered,
        @Schema(description = "Masked unless mpesa-finance or mpesa-admin") @JsonProperty("nominated_number") String nominatedNumber,
        @JsonProperty("callback_url") String callbackUrl,
        @Schema(example = "1000") @JsonProperty("response_status") String responseStatus,
        @JsonProperty("response_description") String responseDescription,
        @JsonProperty("registered_by") String registeredBy,
        @JsonProperty("registered_at") OffsetDateTime registeredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the nightly Pull Transactions run is switched on for this service")
        @JsonProperty("nightly_enabled") boolean nightlyEnabled) {
}
