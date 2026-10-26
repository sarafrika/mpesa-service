package com.sarafrika.apps.mpesaservice.dtos.admin.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Successful money in and out within one bucket; empty buckets are zero, never missing. */
@Schema(name = "TimeseriesPointDto")
public record TimeseriesPointDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Start of the bucket (an hour or a day in Africa/Nairobi)")
        @JsonProperty("bucket_start") Instant bucketStart,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1284350.00")
        @JsonProperty("collections_amount") BigDecimal collectionsAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3412")
        @JsonProperty("collections_count") long collectionsCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "412900.00")
        @JsonProperty("payouts_amount") BigDecimal payoutsAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "186")
        @JsonProperty("payouts_count") long payoutsCount) {
}
