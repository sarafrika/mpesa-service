package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** What an uploaded CSV holds; without preview the valid rows also became a batch waiting for approval. */
@Schema(name = "BulkPayoutUploadDto")
public record BulkPayoutUploadDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("preview") boolean preview,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(schema = @Schema(implementation = BulkPayoutRowDto.class))
        @JsonProperty("rows") List<BulkPayoutRowDto> rows,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("valid_count") int validCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("invalid_count") int invalidCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sum of the valid rows")
        @JsonProperty("valid_total") BigDecimal validTotal,
        @JsonProperty("approval_uuid") UUID approvalUuid,
        @JsonProperty("batch_uuid") UUID batchUuid) {
}
