package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A paybill or till as the grant pickers show it; no credentials. */
@Schema(name = "GrantableShortcode")
public record GrantableShortcodeDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "600100")
        @JsonProperty("shortcode") String shortcode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika Fees")
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PAYBILL")
        @JsonProperty("shortcode_type") String shortcodeType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PRODUCTION")
        @JsonProperty("environment") String environment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("active") boolean active) {

    public static GrantableShortcodeDto from(MpesaShortCode code) {
        return new GrantableShortcodeDto(code.getUuid(), code.getShortcode(), code.getBusinessName(),
                code.getShortcodeType() == null ? null : code.getShortcodeType().name(),
                code.getEnvironment() == null ? null : code.getEnvironment().name(),
                !Boolean.FALSE.equals(code.getIsActive()));
    }
}
