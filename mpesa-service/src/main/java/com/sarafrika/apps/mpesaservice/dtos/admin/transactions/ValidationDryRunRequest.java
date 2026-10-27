package com.sarafrika.apps.mpesaservice.dtos.admin.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** A pretend payment to run through the rules; rules, when given, are tried instead of the saved ones.
 * Leave account or amount out and the service fills in a sample the rules accept. */
@Schema(name = "ValidationDryRunRequest")
public record ValidationDryRunRequest(
        @Schema(example = "ELM-004512", description = "Left out: an account built from the account pattern (400 on "
                + "account when none can be built), the default account reference, or TEST-001")
        @Size(max = 100)
        @JsonProperty("account") String account,
        @Schema(example = "4500.00", description = "Left out: the rule minimum, or 1")
        @DecimalMin(value = "0.01", message = "must be above zero")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(example = "254712345678")
        @Size(max = 100)
        @JsonProperty("msisdn") String msisdn,
        @Schema(description = "Unsaved rules to try; the saved rules are used when absent")
        @Valid
        @JsonProperty("rules") UpdateValidationRulesRequest rules) {
}
