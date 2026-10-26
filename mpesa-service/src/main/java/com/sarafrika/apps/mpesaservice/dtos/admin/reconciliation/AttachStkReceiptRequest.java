package com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(name = "AttachStkReceiptRequest")
public record AttachStkReceiptRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "One of the exception's STK candidates (listReconciliationStkCandidates)")
        @NotNull(message = "is required") @JsonProperty("payment_uuid") UUID paymentUuid) {
}
