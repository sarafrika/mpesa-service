package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Tills whose store number was copied from the till number when store numbers were introduced: STK push,
 * balance and status go out under the store/HO number, so someone must confirm it. */
@Component
@Order(15)
@RequiredArgsConstructor
public class StoreNumberAttentionSource implements AttentionSource {

    static final String KIND = "TILL_STORE_NUMBER_UNCONFIRMED";

    private final MpesaShortCodeRepository shortcodes;

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        return shortcodes.findTillsWithUnconfirmedStoreNumber().stream()
                .filter(code -> scope.includes(code.getUuid()))
                .filter(code -> scope.environment() == null || scope.environment() == code.getEnvironment())
                .map(StoreNumberAttentionSource::item)
                .toList();
    }

    private static AttentionItemDto item(MpesaShortCode code) {
        return new AttentionItemDto(KIND, AttentionItemDto.WARNING,
                "Confirm the store number of till " + code.getShortcode(),
                code.getBusinessName() + " uses " + code.getShortcode() + " as its store/HO number. STK push, "
                        + "balance and status use the number the till went live under; confirm or correct it.",
                "/paybills/" + code.getUuid() + "?tab=overview", code.getUuid());
    }
}
