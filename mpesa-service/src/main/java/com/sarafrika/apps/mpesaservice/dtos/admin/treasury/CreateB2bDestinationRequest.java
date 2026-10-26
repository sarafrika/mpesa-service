package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bReceiverType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A paybill needs the account reference to credit there; a till takes none. */
@Schema(name = "CreateB2bDestinationRequest")
public record CreateB2bDestinationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Sarafrika bank paybill")
        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        @JsonProperty("name") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "522522")
        @NotBlank(message = "is required")
        @Pattern(regexp = "\\d{5,7}", message = "must be a 5 to 7 digit shortcode")
        @JsonProperty("receiver_shortcode") String receiverShortcode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "is required")
        @JsonProperty("receiver_type") B2bReceiverType receiverType,
        @Schema(nullable = true, example = "1234567890")
        @Size(max = 13, message = "must be at most 13 characters")
        @JsonProperty("account_reference") String accountReference,
        @Schema(nullable = true, description = "Defaults to true")
        @JsonProperty("active") Boolean active) {
}
