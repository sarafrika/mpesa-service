package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Asks for a sweep; it waits in Approvals until a checker who is not the maker approves it. */
@Schema(name = "RequestB2bSweepRequest")
public record RequestB2bSweepRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "is required")
        @JsonProperty("destination_uuid") UUID destinationUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "500000", description = "Whole shillings")
        @NotNull(message = "is required")
        @Positive(message = "must be more than 0")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Weekly treasury sweep")
        @NotBlank(message = "is required")
        @Size(max = 500, message = "must be at most 500 characters")
        @JsonProperty("reason") String reason) {
}
