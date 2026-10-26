package com.sarafrika.apps.mpesaservice.dtos.admin.transactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.LookupFailurePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Replaces a shortcode's C2B validation rules; blank pattern or URL means "not used". */
@Schema(name = "UpdateValidationRulesRequest")
public record UpdateValidationRulesRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @JsonProperty("active") Boolean active,
        @Schema(example = "^ELM-[0-9]{6}$", maxLength = 200)
        @Size(max = 200, message = "must be at most 200 characters")
        @JsonProperty("account_pattern") String accountPattern,
        @Schema(example = "10.00")
        @DecimalMin(value = "1.00", message = "must be at least 1.00")
        @Digits(integer = 10, fraction = 2)
        @JsonProperty("min_amount") BigDecimal minAmount,
        @Schema(example = "250000.00")
        @DecimalMin(value = "1.00", message = "must be at least 1.00")
        @Digits(integer = 10, fraction = 2)
        @JsonProperty("max_amount") BigDecimal maxAmount,
        @Schema(example = "https://elimika.example/mpesa/accounts")
        @Size(max = 500)
        @Pattern(regexp = "^$|^https?://\\S+$", message = "must be an http(s) URL")
        @JsonProperty("lookup_url") String lookupUrl,
        @Schema(example = "2000", minimum = "100", maximum = "5000")
        @Min(100)
        @Max(5000)
        @JsonProperty("lookup_timeout_ms") Integer lookupTimeoutMs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @JsonProperty("on_lookup_failure") LookupFailurePolicy onLookupFailure) {
}
