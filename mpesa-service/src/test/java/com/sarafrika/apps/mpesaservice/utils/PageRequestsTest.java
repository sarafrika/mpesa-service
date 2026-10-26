package com.sarafrika.apps.mpesaservice.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

    private static final Map<String, String> SORTABLE = Map.of("created_at", "createdAt", "action", "action");
    private static final Sort DEFAULT = Sort.by(Sort.Order.desc("createdAt"));

    @Test
    void defaultsToTheFirstPageOf25WithTheDefaultSortAndAnIdTiebreaker() {
        PageRequest request = PageRequests.of(null, null, null, SORTABLE, DEFAULT);

        assertThat(request.getPageNumber()).isZero();
        assertThat(request.getPageSize()).isEqualTo(25);
        assertThat(request.getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    @Test
    void clampsSizeAbove100AndRefusesSizeBelow1() {
        assertThat(PageRequests.of(2, 500, null, SORTABLE, DEFAULT).getPageSize()).isEqualTo(100);
        assertThat(PageRequests.of(2, 100, null, SORTABLE, DEFAULT).getPageSize()).isEqualTo(100);
        assertThatThrownBy(() -> PageRequests.of(0, 0, null, SORTABLE, DEFAULT))
                .isInstanceOf(InvalidRequestException.class).extracting("field").isEqualTo("size");
    }

    @Test
    void refusesANegativePage() {
        assertThatThrownBy(() -> PageRequests.of(-1, 25, null, SORTABLE, DEFAULT))
                .isInstanceOf(InvalidRequestException.class).extracting("field").isEqualTo("page");
    }

    @Test
    void readsRepeatableSortsWhetherOrNotSpringSplitTheCommas() {
        Sort joined = PageRequests.of(0, 25, List.of("action,asc", "created_at,desc"), SORTABLE, DEFAULT).getSort();
        Sort split = PageRequests.of(0, 25, List.of("action", "asc", "created_at", "DESC"), SORTABLE, DEFAULT).getSort();
        Sort noDirection = PageRequests.of(0, 25, List.of("action"), SORTABLE, DEFAULT).getSort();

        Sort expected = Sort.by(Sort.Order.asc("action"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        assertThat(joined).isEqualTo(expected);
        assertThat(split).isEqualTo(expected);
        assertThat(noDirection).containsExactly(Sort.Order.asc("action"), Sort.Order.desc("id"));
    }

    @Test
    void refusesUnknownSortFields() {
        assertThatThrownBy(() -> PageRequests.of(0, 25, List.of("consumer_secret,asc"), SORTABLE, DEFAULT))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("consumer_secret")
                .extracting("field").isEqualTo("sort");
        assertThatThrownBy(() -> PageRequests.of(0, 25, List.of("desc"), SORTABLE, DEFAULT))
                .isInstanceOf(InvalidRequestException.class);
        // The tiebreaker is not itself a public sort field.
        assertThatThrownBy(() -> PageRequests.of(0, 25, List.of("id,asc"), SORTABLE, DEFAULT))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void doesNotDuplicateAnIdSortAlreadyPresent() {
        PageRequest request = PageRequests.of(0, 25, null, Map.of(), Sort.by(Sort.Order.asc("id")));

        assertThat(request.getSort()).containsExactly(Sort.Order.asc("id"));
    }
}
