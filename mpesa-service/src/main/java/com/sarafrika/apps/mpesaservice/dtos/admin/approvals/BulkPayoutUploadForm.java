package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** The multipart body of uploadBulkPayout, for the API docs only: the controller reads each part by name. */
@Schema(name = "BulkPayoutUploadForm")
public record BulkPayoutUploadForm(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary",
                description = "CSV with a header row: phone, amount, reference, and optionally name")
        @JsonProperty("file") Object file,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The paybill that pays")
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200,
                description = "Shown to the checker, and on recipients' M-Pesa statements")
        @JsonProperty("title") String title,
        @Schema(maxLength = 1000, description = "Why the batch is paid; required unless preview")
        @JsonProperty("reason") String reason,
        @Schema(defaultValue = "false", description = "Only check the file; nothing is saved")
        @JsonProperty("preview") Boolean preview) {
}
