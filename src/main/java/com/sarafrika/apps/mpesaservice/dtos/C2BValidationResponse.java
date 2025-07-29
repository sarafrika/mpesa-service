package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Standard validation response to send back to Safaricom
 * Must return this from validation URL endpoint
 */
@Schema(description = "C2B validation response to send to Daraja API")
public record C2BValidationResponse(

        @Schema(description = "Result code (0 = accept, 1 = reject)", example = "0")
        @JsonProperty("ResultCode")
        Integer resultCode,

        @Schema(description = "Result description", example = "Accepted")
        @JsonProperty("ResultDesc")
        String resultDesc
) {

    /**
     * Create acceptance response
     */
    public static C2BValidationResponse accept() {
        return new C2BValidationResponse(0, "Accepted");
    }

    /**
     * Create rejection response
     */
    public static C2BValidationResponse reject(String reason) {
        return new C2BValidationResponse(1, reason);
    }
}