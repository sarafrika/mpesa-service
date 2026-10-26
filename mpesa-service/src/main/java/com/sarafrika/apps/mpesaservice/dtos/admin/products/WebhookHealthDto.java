package com.sarafrika.apps.mpesaservice.dtos.admin.products;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** How a webhook has fared over the last 7 days. */
@Schema(name = "WebhookHealth")
public record WebhookHealthDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("status") Status status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1200")
        @JsonProperty("deliveries_7d") long deliveries7d,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1199")
        @JsonProperty("delivered_first_try_7d") long deliveredFirstTry7d,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0.999",
                description = "Share delivered on the first attempt, 0 to 1; 1 when nothing was sent")
        @JsonProperty("first_try_rate_7d") double firstTryRate7d,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0", description = "Waiting for a retry now")
        @JsonProperty("pending_retries") long pendingRetries,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
        @JsonProperty("failed_7d") long failed7d,
        @JsonProperty("last_delivery_at") Instant lastDeliveryAt) {

    /** HEALTHY: nothing failing; DEGRADED: retries pending; FAILING: deliveries gave up; DISABLED: switched off. */
    @Schema(name = "WebhookHealthStatus")
    public enum Status { HEALTHY, DEGRADED, FAILING, NO_TRAFFIC, DISABLED }

    public static WebhookHealthDto of(boolean active, long total, long firstTry, long failed, long retrying,
                                      Instant last) {
        Status status = !active ? Status.DISABLED : failed > 0 ? Status.FAILING : retrying > 0 ? Status.DEGRADED
                : total == 0 ? Status.NO_TRAFFIC : Status.HEALTHY;
        double rate = total == 0 ? 1.0 : (double) firstTry / total;
        return new WebhookHealthDto(status, total, firstTry, rate, retrying, failed, last);
    }
}
