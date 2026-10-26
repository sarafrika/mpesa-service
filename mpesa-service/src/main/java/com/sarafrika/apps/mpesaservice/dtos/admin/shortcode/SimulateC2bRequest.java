package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** A sandbox C2B payment, as if a customer paid the shortcode from their phone. */
@Schema(name = "SimulateC2bRequest")
public record SimulateC2bRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "254708374149")
        @NotBlank(message = "is required")
        @Pattern(regexp = "^254[17][0-9]{8}$", message = "must look like 2547XXXXXXXX or 2541XXXXXXXX")
        @JsonProperty("phone_number") String phoneNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
        @NotNull(message = "is required")
        @DecimalMin(value = "1", message = "must be at least 1")
        @Digits(integer = 8, fraction = 0, message = "must be whole shillings")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(example = "ELM-004512", description = "Account number; defaults to the shortcode's account reference")
        @Size(max = 20, message = "must be at most 20 characters")
        @JsonProperty("bill_ref_number") String billRefNumber) {
}
