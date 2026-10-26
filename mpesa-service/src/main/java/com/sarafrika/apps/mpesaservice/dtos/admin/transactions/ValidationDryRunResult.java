package com.sarafrika.apps.mpesaservice.dtos.admin.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** What Safaricom would have been told: "0" accepts, C2B00011..C2B00016 reject. */
@Schema(name = "ValidationDryRunResult")
public record ValidationDryRunResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("accepted") boolean accepted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "C2B00012")
        @JsonProperty("result_code") String resultCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Account 'X1' does not match ^ELM-[0-9]{6}$")
        @JsonProperty("reason") String reason) {
}
