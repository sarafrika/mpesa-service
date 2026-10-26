package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "RequestReversalRequest")
public record RequestReversalRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The received payment to reverse")
        @NotNull
        @JsonProperty("transaction_uuid") UUID transactionUuid,
        @Schema(description = "Whole shillings; defaults to the original amount and is never more than it",
                example = "15000")
        @Positive
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Parent paid twice for invoice ELM-004512")
        @NotBlank(message = "is required")
        @Size(max = 500)
        @JsonProperty("reason") String reason) {
}
