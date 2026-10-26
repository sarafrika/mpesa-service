package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transaction reversal result callback
 * Received on result URL with the actual reversal status
 */
@Schema(description = "Transaction reversal result callback from Daraja API")
public record TransactionReversalResult(

        @Schema(description = "Result parameters containing reversal details")
        @JsonProperty("Result")
        TransactionReversalResultData result
) {}