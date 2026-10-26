package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "A payout from a shortcode to an M-Pesa number")
public record B2CPayoutRequest(
        @Schema(description = "Configured shortcode to pay from", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(description = "Recipient, 2547XXXXXXXX or 2541XXXXXXXX", example = "254712345678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @JsonProperty("phone_number") String phoneNumber,
        @Schema(description = "Whole shillings", example = "1500", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive
        @JsonProperty("amount") BigDecimal amount,
        @Schema(description = "The caller's own id for this payout; repeating it returns the first payout",
                example = "WD-2026-0042", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100)
        @JsonProperty("client_reference") String clientReference,
        @Schema(description = "Shown on the M-Pesa statement", example = "Elimika withdrawal")
        @Size(max = 100)
        @JsonProperty("remarks") String remarks,
        @Size(max = 100)
        @JsonProperty("occasion") String occasion
) {
}
