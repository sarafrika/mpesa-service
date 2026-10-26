package com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** A run with its exceptions counted per kind, every kind listed even when it has none. */
@Schema(name = "ReconciliationSummaryDto")
public record ReconciliationSummaryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("run") ReconciliationRunDto run,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("kinds") List<KindSummary> kinds) {

    @Schema(name = "ReconciliationKindSummaryDto")
    public record KindSummary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"MISSING_IN_LEDGER", "MISSING_IN_STATEMENT", "AMOUNT_MISMATCH", "DUPLICATE_IN_LEDGER"})
            @JsonProperty("kind") String kind,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("total") long total,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("open") long open,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Statement amount, else ledger amount")
            @JsonProperty("value") BigDecimal value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("suggested_fix") String suggestedFix) {
    }
}
