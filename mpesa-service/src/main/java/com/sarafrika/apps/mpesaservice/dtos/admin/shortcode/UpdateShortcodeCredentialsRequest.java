package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Credentials already changed at Safaricom; a field left empty keeps the stored value, and one must be given. */
@Schema(name = "UpdateShortcodeCredentialsRequest")
public record UpdateShortcodeCredentialsRequest(
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 255, message = "must be at most 255 characters")
        @JsonProperty("consumer_key") String consumerKey,
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 500, message = "must be at most 500 characters")
        @JsonProperty("consumer_secret") String consumerSecret,
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 500, message = "must be at most 500 characters")
        @JsonProperty("passkey") String passkey,
        @Size(max = 100, message = "must be at most 100 characters")
        @JsonProperty("initiator_name") String initiatorName,
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 255, message = "must be at most 255 characters")
        @JsonProperty("initiator_password") String initiatorPassword,
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 4000, message = "must be at most 4000 characters")
        @JsonProperty("security_credential") String securityCredential) {
}
