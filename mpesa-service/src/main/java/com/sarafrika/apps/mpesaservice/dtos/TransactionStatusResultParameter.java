package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Individual result parameter for transaction status
 */
@Schema(description = "Individual transaction status result parameter")
public record TransactionStatusResultParameter(

        @Schema(description = "Parameter name", example = "TransactionAmount")
        @JsonProperty("Key")
        String key,

        @Schema(description = "Parameter value", example = "100.00")
        @JsonProperty("Value")
        String value
) {}