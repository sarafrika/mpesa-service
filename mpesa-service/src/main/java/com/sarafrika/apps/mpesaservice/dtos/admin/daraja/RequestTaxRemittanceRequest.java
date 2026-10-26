package com.sarafrika.apps.mpesaservice.dtos.admin.daraja;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Asks to remit tax to KRA (572572) against a PRN; it waits in Approvals for a checker who is not the maker. */
@Schema(name = "RequestTaxRemittanceRequest")
public record RequestTaxRemittanceRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3000", description = "Whole shillings")
        @NotNull(message = "is required")
        @Positive(message = "must be more than 0")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PRN1234XN",
                description = "The payment registration number KRA issued; sent as AccountReference")
        @NotBlank(message = "is required")
        @Size(max = 50, message = "must be at most 50 characters")
        @JsonProperty("prn") String prn,
        @Schema(example = "VAT October", description = "Up to 100 characters (Safaricom); defaults to \"Tax <PRN>\"")
        @Size(max = 100, message = "must be at most 100 characters")
        @JsonProperty("remarks") String remarks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "October VAT")
        @NotBlank(message = "is required")
        @Size(max = 500, message = "must be at most 500 characters")
        @JsonProperty("reason") String reason) {
}
