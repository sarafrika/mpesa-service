package com.sarafrika.apps.mpesaservice.dtos.admin.qr;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A generated QR code; image_base64 is filled only in the answer to its creation, lists link image_path. */
@Schema(name = "QrCodeDto")
public record QrCodeDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("merchant_name") String merchantName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("ref_no") String refNo,
        @Schema(nullable = true)
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"PB", "BG"})
        @JsonProperty("trx_code") String trxCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Credit party identifier: the shortcode")
        @JsonProperty("cpi") String cpi,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("size") int size,
        @Schema(nullable = true, description = "Daraja's RequestID")
        @JsonProperty("request_id") String requestId,
        @Schema(nullable = true)
        @JsonProperty("created_by") String createdBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                example = "/api/v1/admin/shortcodes/550e8400-e29b-41d4-a716-446655440000/qr-codes/7c9e6679-7425-40de-944b-e07fc1f90ae7/image",
                description = "Admin API path of the PNG")
        @JsonProperty("image_path") String imagePath,
        @Schema(nullable = true, description = "The PNG, base64; only when the code was just created")
        @JsonProperty("image_base64") String imageBase64) {
}
