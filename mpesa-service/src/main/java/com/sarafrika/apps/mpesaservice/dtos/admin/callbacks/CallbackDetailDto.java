package com.sarafrika.apps.mpesaservice.dtos.admin.callbacks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** One inbox entry with what Safaricom sent; phones and names are masked unless the caller is finance or admin. */
@Schema(name = "CallbackDetailDto")
public record CallbackDetailDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("callback") CallbackDto callback,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Selected request headers (never auth)")
        @JsonProperty("headers") Map<String, String> headers,
        @Schema(implementation = Object.class, type = "object", nullable = true,
                additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
                description = "The body as received; null when it was too large")
        @JsonProperty("body") JsonNode body,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Body size in bytes")
        @JsonProperty("body_size") int bodySize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True when the body was over the cap and not kept")
        @JsonProperty("body_truncated") boolean bodyTruncated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether an operator may replay it now")
        @JsonProperty("replayable") boolean replayable,
        @Schema(nullable = true, description = "Why it cannot be replayed now")
        @JsonProperty("replay_blocked_reason") String replayBlockedReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "A failed B2B or reversal result that "
                + "finance may attach to its transfer or reversal by hand")
        @JsonProperty("attachable") boolean attachable) {
}
