package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Individual result parameter for transaction reversal
 */
@Schema(description = "Individual transaction reversal result parameter")
public record TransactionReversalResultParameter(

        @Schema(description = "Parameter name", example = "Amount")
        @JsonProperty("Key")
        String key,

        @Schema(description = "Parameter value", example = "100.00")
        @JsonProperty("Value")
        String value
) {}