package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.dtos.AccountBalanceResultData;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Account balance result callback
 * Received on result URL with the actual account balance details
 */
@Schema(description = "Account balance result callback from Daraja API")
public record AccountBalanceResult(

        @Schema(description = "Result parameters containing account balance details")
        @JsonProperty("Result")
        AccountBalanceResultData result
) {}