package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** One check made when the request was raised, for the checker to read before deciding. */
@Schema(name = "PreCheckDto")
public record PreCheckDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"OK", "WARN", "FAIL"})
        @JsonProperty("state") String state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Not reversed before")
        @JsonProperty("text") String text) {

    public static final String OK = "OK";
    public static final String WARN = "WARN";
    public static final String FAIL = "FAIL";

    public static PreCheckDto ok(String text) {
        return new PreCheckDto(OK, text);
    }

    public static PreCheckDto warn(String text) {
        return new PreCheckDto(WARN, text);
    }
}
