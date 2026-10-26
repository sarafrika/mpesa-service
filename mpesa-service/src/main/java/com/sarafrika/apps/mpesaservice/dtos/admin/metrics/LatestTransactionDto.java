package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A recent payment in either direction, without customer details. */
@Schema(name = "LatestTransactionDto")
public record LatestTransactionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"IN", "OUT"})
        @JsonProperty("direction") String direction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "C2B",
                description = "STK_PUSH or C2B for money in; B2C or B2B for money out")
        @JsonProperty("payment_type") String paymentType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SUCCESS")
        @JsonProperty("status") String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4500.00")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(nullable = true, example = "TJQ4K2L9XA",
                description = "M-Pesa receipt, or the request id while there is none yet")
        @JsonProperty("reference") String reference,
        @Schema(nullable = true, example = "ELM-004512")
        @JsonProperty("account_reference") String accountReference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "600100")
        @JsonProperty("shortcode") String shortcode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika Fees")
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") Instant createdAt) {
}
