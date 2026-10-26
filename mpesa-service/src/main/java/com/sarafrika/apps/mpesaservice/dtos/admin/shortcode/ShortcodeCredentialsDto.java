package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Credentials as the console may see them: masked to the last four characters, or only whether set. */
@Schema(name = "ShortcodeCredentials")
public record ShortcodeCredentialsDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "••••Qx7d")
        @JsonProperty("consumer_key_masked") String consumerKeyMasked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "••••9KfA")
        @JsonProperty("consumer_secret_masked") String consumerSecretMasked,
        @Schema(example = "••••b21c", description = "Null when no passkey is stored (STK push is off)")
        @JsonProperty("passkey_masked") String passkeyMasked,
        @Schema(example = "elimika-api", description = "The B2C initiator (API operator) name; not a secret")
        @JsonProperty("initiator_name") String initiatorName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("initiator_password_set") boolean initiatorPasswordSet,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("security_credential_set") boolean securityCredentialSet,
        @Schema(description = "When the stored credentials were last saved here")
        @JsonProperty("updated_at") LocalDateTime updatedAt) {
}
