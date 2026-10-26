package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** The callback URLs this service gives Safaricom, from its mpesa.callback configuration. */
@Schema(name = "ShortcodeCallbackUrls")
public record ShortcodeCallbackUrlsDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("stk_callback_url") String stkCallbackUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("c2b_confirmation_url") String c2bConfirmationUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("c2b_validation_url") String c2bValidationUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("b2c_result_url") String b2cResultUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("b2c_timeout_url") String b2cTimeoutUrl) {
}
