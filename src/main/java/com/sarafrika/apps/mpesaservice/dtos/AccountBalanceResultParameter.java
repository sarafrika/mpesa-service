package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Individual result parameter for account balance
 */
@Schema(description = "Individual account balance result parameter")
public record AccountBalanceResultParameter(

        @Schema(description = "Parameter name", example = "AccountBalance")
        @JsonProperty("Key")
        String key,

        @Schema(description = "Parameter value", example = "10000.00|KES|10000.00|0.00")
        @JsonProperty("Value")
        String value
) {}