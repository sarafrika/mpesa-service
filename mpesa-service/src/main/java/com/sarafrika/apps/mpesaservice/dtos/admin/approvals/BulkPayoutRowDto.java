package com.sarafrika.apps.mpesaservice.dtos.admin.approvals;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** One CSV row as read; an invalid row says why and is left out of the batch. */
@Schema(name = "BulkPayoutRowDto")
public record BulkPayoutRowDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2", description = "Line in the file")
        @JsonProperty("line") int line,
        @Schema(description = "Normalised to 2547XXXXXXXX when valid; masked unless the caller has mpesa-finance")
        @JsonProperty("phone") String phone,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("reference") String reference,
        @JsonProperty("name") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("valid") boolean valid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(schema = @Schema(example = "amount must be a whole number of shillings"))
        @JsonProperty("problems") List<String> problems) {
}
