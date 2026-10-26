package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Our answer to Safaricom's C2B validation request: "0" accepts, a C2B000xx code rejects. */
@Schema(description = "C2B validation response to send to Daraja API")
public record C2BValidationResponse(

        @Schema(description = "\"0\" accepts; C2B00011-C2B00016 reject", example = "0")
        @JsonProperty("ResultCode")
        String resultCode,

        @Schema(description = "Accepted or Rejected", example = "Accepted")
        @JsonProperty("ResultDesc")
        String resultDesc
) {

    public static C2BValidationResponse accept() {
        return new C2BValidationResponse("0", "Accepted");
    }

    public static C2BValidationResponse reject(String resultCode) {
        return new C2BValidationResponse(resultCode, "Rejected");
    }
}
