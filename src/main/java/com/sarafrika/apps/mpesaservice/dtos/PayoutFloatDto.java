package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "A shortcode's float as Safaricom last reported it on a payout result")
public record PayoutFloatDto(
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @JsonProperty("utility_account_available_funds") BigDecimal utilityAccountAvailableFunds,
        @JsonProperty("working_account_available_funds") BigDecimal workingAccountAvailableFunds,
        @Schema(description = "When Safaricom reported it; null when no payout has completed yet")
        @JsonProperty("reported_at") LocalDateTime reportedAt
) {
}
