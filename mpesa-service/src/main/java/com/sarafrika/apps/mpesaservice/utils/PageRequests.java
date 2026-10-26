package com.sarafrika.apps.mpesaservice.utils;

import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Admin list parameters as a PageRequest: page 0-based and not negative, size 1..100 (larger is clamped),
 * sort {@code field,asc|desc} from a whitelist, always ending in id desc so pages never repeat rows. */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;
    private static final String TIEBREAKER = "id";

    private PageRequests() {
    }

    /** allowedSortFields maps API names (created_at) to entity properties (createdAt); defaultSort applies
     * when no sort is given. */
    public static PageRequest of(Integer page, Integer size, List<String> sort,
                                 Map<String, String> allowedSortFields, Sort defaultSort) {
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw new InvalidRequestException("page", "must be 0 or greater");
        }
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1) {
            throw new InvalidRequestException("size", "must be between 1 and " + MAX_SIZE);
        }
        Sort requested = parse(sort, allowedSortFields);
        Sort order = requested.isSorted() ? requested : defaultSort;
        if (order.getOrderFor(TIEBREAKER) == null) {
            order = order.and(Sort.by(Sort.Order.desc(TIEBREAKER)));
        }
        return PageRequest.of(pageNumber, Math.min(pageSize, MAX_SIZE), order);
    }

    // Spring may split "created_at,desc" into two values, so tokens are re-paired: a field, then an optional direction.
    private static Sort parse(List<String> sort, Map<String, String> allowed) {
        if (sort == null) {
            return Sort.unsorted();
        }
        List<String> tokens = sort.stream().filter(s -> s != null)
                .flatMap(s -> Arrays.stream(s.split(","))).map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<Sort.Order> orders = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String field = tokens.get(i);
            if (isDirection(field)) {
                throw new InvalidRequestException("sort", "expected field,asc|desc but got '" + field + "' first");
            }
            String property = allowed.get(field);
            if (property == null) {
                throw new InvalidRequestException("sort", "cannot sort by '" + field + "'; allowed: "
                        + String.join(", ", allowed.keySet().stream().sorted().toList()));
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (i + 1 < tokens.size() && isDirection(tokens.get(i + 1))) {
                direction = Sort.Direction.fromString(tokens.get(++i));
            }
            orders.add(new Sort.Order(direction, property));
        }
        return Sort.by(orders);
    }

    private static boolean isDirection(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        return t.equals("asc") || t.equals("desc");
    }
}
