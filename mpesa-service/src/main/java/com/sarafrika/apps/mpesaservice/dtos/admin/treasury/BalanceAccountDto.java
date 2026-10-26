package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** One account from an Account Balance result, as Safaricom listed it. */
@Schema(name = "BalanceAccountDto")
public record BalanceAccountDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Utility Account")
        @JsonProperty("name") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "KES")
        @JsonProperty("currency") String currency,
        @Schema(nullable = true, example = "1640210.00")
        @JsonProperty("available") BigDecimal available,
        @Schema(nullable = true, example = "1640210.00")
        @JsonProperty("current") BigDecimal current,
        @Schema(nullable = true, example = "0.00")
        @JsonProperty("reserved") BigDecimal reserved,
        @Schema(nullable = true, example = "0.00")
        @JsonProperty("uncleared") BigDecimal uncleared) {
}
