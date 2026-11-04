package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A shortcode's float: the newest of its Account Balance results, payout reports and C2B OrgAccountBalance")
public record PayoutFloatDto(
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @JsonProperty("utility_account_available_funds") BigDecimal utilityAccountAvailableFunds,
        @JsonProperty("working_account_available_funds") BigDecimal workingAccountAvailableFunds,
        @Schema(description = "When Safaricom reported it; null when no balance is known yet")
        @JsonProperty("reported_at") Instant reportedAt
) {
}
