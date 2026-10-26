package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.dtos.B2BResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * B2B transfer result callback
 * Received on result URL after B2B transfer processing completes
 */
@Schema(description = "B2B transfer result callback from Daraja API")
public record B2BTransferResult(

        @Schema(description = "Result parameters containing transaction details")
        @JsonProperty("Result")
        B2BResult result
) {}