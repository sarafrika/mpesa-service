package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Individual result parameter for B2B
 */
@Schema(description = "Individual B2B result parameter")
public record B2BResultParameter(

        @Schema(description = "Parameter name", example = "Amount")
        @JsonProperty("Key")
        String key,

        @Schema(description = "Parameter value", example = "1000.00")
        @JsonProperty("Value")
        String value
) {}