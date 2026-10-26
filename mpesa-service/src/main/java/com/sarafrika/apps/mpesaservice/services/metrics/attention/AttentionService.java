package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Gathers every AttentionSource bean's items, most severe first; one failing source never hides the rest. */
@Service
@Slf4j
public class AttentionService {

    public static final int MAX_ITEMS = 50;
    private static final Map<String, Integer> RANK = Map.of(
            AttentionItemDto.CRITICAL, 0, AttentionItemDto.WARNING, 1, AttentionItemDto.INFO, 2);

    private final List<AttentionSource> sources;

    public AttentionService(List<AttentionSource> sources) {
        this.sources = sources;
    }

    public List<AttentionItemDto> items(AttentionScope scope) {
        List<AttentionItemDto> items = new ArrayList<>();
        for (AttentionSource source : sources) {
            try {
                items.addAll(source.currentItems(scope));
            } catch (RuntimeException e) {
                log.warn("Attention source {} failed: {}", source.getClass().getSimpleName(), e.getMessage());
            }
        }
        return items.stream()
                .sorted(Comparator.comparing((AttentionItemDto item) -> RANK.getOrDefault(item.level(), 3)))
                .limit(MAX_ITEMS)
                .toList();
    }
}
