package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.C2bRegistration;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

/** One C2B URL registration attempt and Safaricom's answer. */
@Schema(name = "C2bRegistration")
public record C2bRegistrationDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("response_type") C2bResponseType responseType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("confirmation_url") String confirmationUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("validation_url") String validationUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("successful") boolean successful,
        @Schema(example = "0")
        @JsonProperty("result_code") String resultCode,
        @Schema(example = "Success")
        @JsonProperty("result_desc") String resultDesc,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "jane.doe")
        @JsonProperty("registered_by") String registeredBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") LocalDateTime createdAt) {

    public static C2bRegistrationDto from(C2bRegistration registration) {
        return new C2bRegistrationDto(registration.getUuid(), C2bResponseType.valueOf(registration.getResponseType()),
                registration.getConfirmationUrl(), registration.getValidationUrl(), registration.isSuccessful(),
                registration.getResultCode(), registration.getResultDesc(), registration.getRegisteredBy(),
                registration.getCreatedAt());
    }
}
