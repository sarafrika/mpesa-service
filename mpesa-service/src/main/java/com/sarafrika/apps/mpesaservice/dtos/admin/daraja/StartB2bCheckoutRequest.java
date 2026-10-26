package com.sarafrika.apps.mpesaservice.dtos.admin.daraja;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Sends a USSD push to a merchant's till asking it to pay this paybill. */
@Schema(name = "StartB2bCheckoutRequest")
public record StartB2bCheckoutRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "000001",
                description = "The paying merchant's till (primaryShortCode); its nominated number gets the prompt")
        @NotBlank(message = "is required")
        @Pattern(regexp = "\\d{5,10}", message = "must be 5 to 10 digits")
        @JsonProperty("payer_till") String payerTill,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "100", description = "Whole shillings")
        @NotNull(message = "is required")
        @Positive(message = "must be more than 0")
        @JsonProperty("amount") BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "INV-2041",
                description = "Shown to the merchant in the prompt; this service stores up to 50 characters")
        @NotBlank(message = "is required")
        @Size(max = 50, message = "must be at most 50 characters")
        @JsonProperty("payment_ref") String paymentRef,
        @Schema(example = "Sarafrika", description = "The name the merchant knows us by; defaults to the business name")
        @Size(max = 100, message = "must be at most 100 characters")
        @JsonProperty("partner_name") String partnerName) {
}
