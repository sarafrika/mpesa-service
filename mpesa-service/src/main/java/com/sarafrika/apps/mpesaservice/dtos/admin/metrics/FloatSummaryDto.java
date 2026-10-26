package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Float across active shortcodes: each one's latest reported balance, summed. */
@Schema(name = "FloatSummaryDto")
public record FloatSummaryDto(
        @Schema(nullable = true, example = "2410000.00",
                description = "Null until at least one shortcode has reported a balance")
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3",
                description = "How many shortcodes have reported a balance")
        @JsonProperty("shortcodes_reporting") int shortcodesReporting,
        @Schema(nullable = true, description = "The shortcode with the least float")
        @JsonProperty("lowest") ShortcodeFloatDto lowest) {
}
