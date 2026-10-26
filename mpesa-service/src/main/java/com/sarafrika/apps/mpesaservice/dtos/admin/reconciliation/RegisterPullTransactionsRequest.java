package com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** NominatedNumber: the Safaricom number associated with the organisation account (Pull Transactions Register). */
@Schema(name = "RegisterPullTransactionsRequest")
public record RegisterPullTransactionsRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "254722000000")
        @NotBlank(message = "is required")
        @Pattern(regexp = "^(\\+?254|0)?[17]\\d{8}$", message = "must be a Safaricom number such as 0722000000")
        @JsonProperty("nominated_number") String nominatedNumber) {
}
