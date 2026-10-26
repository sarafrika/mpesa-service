package com.sarafrika.apps.mpesaservice.dtos.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.http.HttpStatus;

/** The error body every admin endpoint returns; field_errors is empty unless validation failed. */
public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "404")
        @JsonProperty("status") int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Not Found")
        @JsonProperty("error") String error,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Shortcode not found")
        @JsonProperty("message") String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("field_errors") List<FieldError> fieldErrors) {

    public static ApiError of(HttpStatus status, String message) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, List.of());
    }

    public static ApiError of(HttpStatus status, String message, List<FieldError> fieldErrors) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, fieldErrors);
    }

    @Schema(name = "ApiFieldError")
    public record FieldError(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "size")
            @JsonProperty("field") String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "must be less than or equal to 100")
            @JsonProperty("message") String message) {
    }
}
