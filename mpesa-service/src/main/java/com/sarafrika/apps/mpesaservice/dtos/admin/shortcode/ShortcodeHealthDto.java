package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What the console checks on a shortcode: token, C2B URLs, recent payments and float. */
@Schema(name = "ShortcodeHealth")
public record ShortcodeHealthDto(
        @Schema(description = "Outcome of the last Daraja token check; null when never run")
        @JsonProperty("token_ok") Boolean tokenOk,
        @JsonProperty("token_checked_at") LocalDateTime tokenCheckedAt,
        @Schema(example = "312")
        @JsonProperty("token_latency_ms") Integer tokenLatencyMs,
        @JsonProperty("token_error") String tokenError,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("c2b_state") C2bRegistrationState c2bState,
        @Schema(description = "When the newest C2B registration attempt was made")
        @JsonProperty("c2b_registered_at") LocalDateTime c2bRegisteredAt,
        @Schema(description = "Newest incoming payment (STK or C2B) on this shortcode")
        @JsonProperty("last_payment_at") LocalDateTime lastPaymentAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "902140.00",
                description = "Successful incoming payments since midnight, server time, by when the money "
                        + "arrived (transaction time, else when it was stored)")
        @JsonProperty("collected_today") BigDecimal collectedToday,
        @Schema(description = "Utility balance from the newest Account Balance result or payout result; null when unknown")
        @JsonProperty("float_utility") BigDecimal floatUtility,
        @JsonProperty("float_working") BigDecimal floatWorking,
        @JsonProperty("float_reported_at") LocalDateTime floatReportedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Successful B2C payouts since midnight")
        @JsonProperty("paid_out_today") BigDecimal paidOutToday,
        @Schema(description = "Set when the newest payout result says the credentials are wrong (B2C ResultCode 2001)")
        @JsonProperty("credentials_hint") String credentialsHint,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0",
                description = "Callbacks that failed in the last 24 h on this shortcode's token URL or linked payments")
        @JsonProperty("callback_failures_24h") long callbackFailures24h) {
}
