package com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Safaricom only returns Pull Transactions from the last 48 hours (developer.safaricom.co.ke/apis/PullTransaction). */
@Schema(name = "PullReconciliationRequest")
public record PullReconciliationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Choose a paybill") @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-25T00:00:00+03:00")
        @NotNull(message = "is required") @JsonProperty("from") OffsetDateTime from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-26T00:00:00+03:00",
                description = "Exclusive end")
        @NotNull(message = "is required") @JsonProperty("to") OffsetDateTime to) {
}
