package com.sarafrika.apps.mpesaservice.dtos.admin.callbacks;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** Inbox counts by status for callbacks received since {@code since} (the last 24 hours). */
@Schema(name = "CallbackSummaryDto")
public record CallbackSummaryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("since") OffsetDateTime since,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Every callback received")
        @JsonProperty("total") long total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Still being handled")
        @JsonProperty("received") long received,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("processed") long processed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Handled but not applied; rejected callbacks are not counted here")
        @JsonProperty("failed") long failed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Failed, then succeeded on replay")
        @JsonProperty("replayed") long replayed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Refused before any handler ran (unknown callback URL); nothing to apply or replay")
        @JsonProperty("rejected") long rejected) {

    public CallbackSummaryDto(OffsetDateTime since, long total, long received, long processed, long failed,
                              long replayed) {
        this(since, total, received, processed, failed, replayed, 0);
    }
}
