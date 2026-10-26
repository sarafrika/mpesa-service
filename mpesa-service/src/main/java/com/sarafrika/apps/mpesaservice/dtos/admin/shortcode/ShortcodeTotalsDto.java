package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Figures across every shortcode, for the cards above the paybills list. */
@Schema(name = "ShortcodeTotals")
public record ShortcodeTotalsDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
        @JsonProperty("total") long total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
        @JsonProperty("active") long active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("suspended") long suspended,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("draft") long draft,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("production") long production,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("sandbox") long sandbox,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Active shortcodes that take C2B payments")
        @JsonProperty("c2b_applicable") long c2bApplicable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Of those, how many are registered")
        @JsonProperty("c2b_registered") long c2bRegistered,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2412680.00",
                description = "Sum of the newest known utility balance of every shortcode")
        @JsonProperty("total_float") BigDecimal totalFloat,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Shortcodes with a known float")
        @JsonProperty("float_known") long floatKnown,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "B2C results in the last 24 hours with ResultCode 2001 (invalid initiator)")
        @JsonProperty("initiator_errors_24h") long initiatorErrors24h) {
}
