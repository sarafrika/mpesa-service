package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceQuerySource;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceSnapshotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** An Account Balance query and, once Safaricom answered, the balances it reported. */
@Schema(name = "BalanceSnapshotDto")
public record BalanceSnapshotDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("status") BalanceSnapshotStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("source") BalanceQuerySource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Empty until the result arrives")
        @JsonProperty("accounts") List<BalanceAccountDto> accounts,
        @Schema(nullable = true, example = "1640210.00", description = "Utility account available balance")
        @JsonProperty("utility_available") BigDecimal utilityAvailable,
        @Schema(nullable = true, example = "0.00")
        @JsonProperty("working_available") BigDecimal workingAvailable,
        @Schema(nullable = true, example = "4812.00")
        @JsonProperty("charges_paid_available") BigDecimal chargesPaidAvailable,
        @Schema(nullable = true, example = "0")
        @JsonProperty("result_code") Integer resultCode,
        @Schema(nullable = true, example = "The service request is processed successfully.")
        @JsonProperty("result_desc") String resultDesc,
        @Schema(nullable = true, description = "Who asked; system for the scheduled query")
        @JsonProperty("requested_by") String requestedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("requested_at") OffsetDateTime requestedAt,
        @Schema(nullable = true, description = "When Safaricom's result (or timeout) arrived")
        @JsonProperty("received_at") OffsetDateTime receivedAt) {
}
