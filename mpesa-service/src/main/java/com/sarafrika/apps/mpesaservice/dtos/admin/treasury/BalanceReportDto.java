package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceQuerySource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One time Safaricom reported the shortcode's balance: a balance result, a settled payout or a C2B payment. */
@Schema(name = "BalanceReportDto")
public record BalanceReportDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"BALANCE_QUERY", "PAYOUT", "C2B_PAYMENT"},
                description = "What carried the report")
        @JsonProperty("kind") String kind,
        @Schema(nullable = true, description = "How the balance query was sent; null for other kinds")
        @JsonProperty("query_source") BalanceQuerySource querySource,
        @Schema(nullable = true, example = "1640210.00", description = "Utility account available balance")
        @JsonProperty("utility_available") BigDecimal utilityAvailable,
        @Schema(nullable = true, example = "0.00", description = "Working account available balance; "
                + "C2B payments do not report it")
        @JsonProperty("working_available") BigDecimal workingAvailable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("reported_at") OffsetDateTime reportedAt,
        @Schema(nullable = true, description = "Who asked for the balance query; null for other kinds")
        @JsonProperty("requested_by") String requestedBy,
        @Schema(nullable = true, example = "UJ7T4AB1CD", description = "The payout's or payment's M-Pesa receipt")
        @JsonProperty("reference") String reference,
        @Schema(nullable = true, example = "1500.00", description = "The payout's or payment's amount; "
                + "null for balance queries")
        @JsonProperty("amount") BigDecimal amount) {
}
