package com.sarafrika.apps.mpesaservice.services.reconciliation;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReconciliationExceptionRepository;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionScope;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Paybills with reconciliation exceptions nobody has resolved yet, one item per paybill. */
@Component
@Order(40)
@RequiredArgsConstructor
public class OpenExceptionsAttentionSource implements AttentionSource {

    private final ReconciliationExceptionRepository exceptions;
    private final MpesaShortCodeRepository shortcodes;

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        List<AttentionItemDto> items = new ArrayList<>();
        for (Object[] row : exceptions.countOpenByShortcode()) {
            UUID uuid = (UUID) row[0];
            long count = (Long) row[1];
            if (!scope.includes(uuid)) {
                continue;
            }
            MpesaShortCode shortcode = shortcodes.findByUuid(uuid).orElse(null);
            if (shortcode == null || (scope.environment() != null && scope.environment() != shortcode.getEnvironment())) {
                continue;
            }
            String name = shortcode.getBusinessName() + " · " + shortcode.getShortcode();
            items.add(new AttentionItemDto("RECONCILIATION_EXCEPTIONS", AttentionItemDto.WARNING,
                    count + (count == 1 ? " reconciliation exception open on " : " reconciliation exceptions open on ")
                            + name,
                    "Receipts that did not match Safaricom's record. Backfill, query or resolve them.",
                    "/reconciliation?paybill=" + uuid, uuid));
        }
        return items;
    }
}
