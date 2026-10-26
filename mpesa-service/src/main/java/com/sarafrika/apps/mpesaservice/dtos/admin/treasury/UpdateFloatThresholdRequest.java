package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Null clears the shortcode's own threshold so it falls back to the service default. */
@Schema(name = "UpdateFloatThresholdRequest")
public record UpdateFloatThresholdRequest(
        @Schema(nullable = true, example = "200000.00")
        @PositiveOrZero(message = "must be 0 or more")
        @DecimalMax(value = "9999999999999.99", message = "is too large")
        @Digits(integer = 13, fraction = 2, message = "must have at most 2 decimals")
        @JsonProperty("low_float_threshold") BigDecimal lowFloatThreshold) {
}
