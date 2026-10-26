package com.sarafrika.apps.mpesaservice.dtos.admin.daraja;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Asks to pay a Pochi la Biashara wallet; it waits in Approvals for a checker who is not the maker.
 * KES 10 to 250,000 per payment and remarks of 2 to 100 characters are Safaricom's documented limits
 * (developer.safaricom.co.ke/apis/BusinessToPochi). */
@Schema(name = "RequestPochiPayoutRequest")
public record RequestPochiPayoutRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "254705912645",
                description = "The wallet's 12-digit MSISDN, without the plus sign")
        @NotBlank(message = "is required")
        @Pattern(regexp = "254\\d{9}", message = "must be a 12-digit number starting 254")
        @JsonProperty("phone_number") String phoneNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1500", description = "Whole shillings")
        @NotNull(message = "is required")
        @DecimalMin(value = "10", message = "must be at least KES 10 (Safaricom's minimum)")
        @DecimalMax(value = "250000", message = "must be at most KES 250,000 (Safaricom's maximum)")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Supplier payment")
        @NotBlank(message = "is required")
        @Size(min = 2, max = 100, message = "must be 2 to 100 characters")
        @JsonProperty("remarks") String remarks,
        @Schema(example = "October stock")
        @Size(max = 100, message = "must be at most 100 characters")
        @JsonProperty("occasion") String occasion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Stock bought from the stall")
        @NotBlank(message = "is required")
        @Size(max = 500, message = "must be at most 500 characters")
        @JsonProperty("reason") String reason) {
}
