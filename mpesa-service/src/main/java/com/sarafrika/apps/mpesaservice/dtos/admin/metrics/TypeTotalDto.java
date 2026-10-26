package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(name = "TypeTotalDto")
public record TypeTotalDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "C2B")
        @JsonProperty("payment_type") String paymentType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "902140.00")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2986")
        @JsonProperty("count") long count) {
}
