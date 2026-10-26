package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transaction status result callback
 * Received on result URL with the actual transaction status details
 */
@Schema(description = "Transaction status result callback from Daraja API")
public record TransactionStatusResult(

        @Schema(description = "Result parameters containing transaction status details")
        @JsonProperty("Result")
        TransactionStatusResultData result
) {}