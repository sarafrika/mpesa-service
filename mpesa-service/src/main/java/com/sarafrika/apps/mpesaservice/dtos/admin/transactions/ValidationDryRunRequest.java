package com.sarafrika.apps.mpesaservice.dtos.admin.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** A pretend payment to run through the rules; rules, when given, are tried instead of the saved ones. */
@Schema(name = "ValidationDryRunRequest")
public record ValidationDryRunRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ELM-004512")
        @NotNull
        @Size(max = 100)
        @JsonProperty("account") String account,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4500.00")
        @NotNull
        @DecimalMin(value = "0.01", message = "must be above zero")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(example = "254712345678")
        @Size(max = 100)
        @JsonProperty("msisdn") String msisdn,
        @Schema(description = "Unsaved rules to try; the saved rules are used when absent")
        @Valid
        @JsonProperty("rules") UpdateValidationRulesRequest rules) {
}
