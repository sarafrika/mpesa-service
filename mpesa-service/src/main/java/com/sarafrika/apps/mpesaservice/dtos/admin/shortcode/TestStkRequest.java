package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** A small real STK push from the console to prove a live shortcode end to end. */
@Schema(name = "TestStkRequest")
public record TestStkRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "254708374149")
        @NotBlank(message = "is required")
        @Pattern(regexp = "^254[17][0-9]{8}$", message = "must look like 2547XXXXXXXX or 2541XXXXXXXX")
        @JsonProperty("phone_number") String phoneNumber,
        @Schema(example = "1", defaultValue = "1", maximum = "10", description = "Whole shillings, at most KES 10")
        @DecimalMin(value = "1", message = "must be at least 1")
        @DecimalMax(value = "10", message = "must be at most KES 10 for a test")
        @Digits(integer = 2, fraction = 0, message = "must be whole shillings")
        @JsonProperty("amount") BigDecimal amount) {

    public BigDecimal amountOrDefault() {
        return amount == null ? BigDecimal.ONE : amount;
    }
}
