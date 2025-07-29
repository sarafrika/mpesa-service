package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.dtos.B2CResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * B2C payment result callback
 * Received on result URL after B2C payment processing completes
 */
@Schema(description = "B2C payment result callback from Daraja API")
public record B2CPaymentResult(

        @Schema(description = "Result parameters containing transaction details")
        @JsonProperty("Result")
        B2CResult result
) {}