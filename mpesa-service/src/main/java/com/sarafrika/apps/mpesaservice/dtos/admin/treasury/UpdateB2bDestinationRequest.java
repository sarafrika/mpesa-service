package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bReceiverType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Only the fields sent change; an empty account_reference clears it. */
@Schema(name = "UpdateB2bDestinationRequest")
public record UpdateB2bDestinationRequest(
        @Schema(nullable = true)
        @Size(min = 1, max = 100, message = "must be 1 to 100 characters")
        @JsonProperty("name") String name,
        @Schema(nullable = true)
        @Pattern(regexp = "\\d{5,7}", message = "must be a 5 to 7 digit shortcode")
        @JsonProperty("receiver_shortcode") String receiverShortcode,
        @Schema(nullable = true)
        @JsonProperty("receiver_type") B2bReceiverType receiverType,
        @Schema(nullable = true)
        @Size(max = 13, message = "must be at most 13 characters")
        @JsonProperty("account_reference") String accountReference,
        @Schema(nullable = true)
        @JsonProperty("active") Boolean active) {
}
