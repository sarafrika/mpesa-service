package com.sarafrika.apps.mpesaservice.dtos.admin.qr;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** A Dynamic QR code for this shortcode; the shortcode itself is the CPI (credit party) Daraja pays. */
@Schema(name = "CreateQrCodeRequest")
public record CreateQrCodeRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika Fees",
                description = "Daraja MerchantName: the name the customer sees")
        @NotBlank @Size(max = 100)
        @JsonProperty("merchant_name") String merchantName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ELM-004512",
                description = "Daraja RefNo: the account or invoice reference")
        @NotBlank @Size(max = 100)
        @JsonProperty("ref_no") String refNo,
        @Schema(nullable = true, example = "1500", description = "Daraja Amount in KES; leave out for any amount")
        @DecimalMin(value = "1", message = "must be at least 1") @Digits(integer = 13, fraction = 2)
        @JsonProperty("amount") BigDecimal amount,
        @Schema(nullable = true, allowableValues = {"PB", "BG"},
                description = "Daraja TrxCode: PB (Paybill) or BG (Buy Goods). Defaults from the shortcode type; "
                        + "SM, WA and SB pay a phone, agent or business number, never one of our shortcodes")
        @Pattern(regexp = "PB|BG", message = "must be PB or BG")
        @JsonProperty("trx_code") String trxCode,
        @Schema(nullable = true, example = "300", description = "Daraja Size: the square image's side in pixels")
        @Min(100) @Max(1000)
        @JsonProperty("size") Integer size) {
}
