package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Individual result parameter
 */
@Schema(description = "Individual B2C result parameter")
public record B2CResultParameter(

        @Schema(description = "Parameter name", example = "TransactionAmount")
        @JsonProperty("Key")
        String key,

        @Schema(description = "Parameter value", example = "100.00")
        @JsonProperty("Value")
        String value
) {}