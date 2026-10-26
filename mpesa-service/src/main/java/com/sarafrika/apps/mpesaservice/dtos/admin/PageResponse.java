package com.sarafrika.apps.mpesaservice.dtos.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** One page of an admin listing; {@code page} is zero-based. */
public record PageResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("items") List<T> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
        @JsonProperty("page") int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "25")
        @JsonProperty("size") int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "120")
        @JsonProperty("total_items") long totalItems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
        @JsonProperty("total_pages") int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
