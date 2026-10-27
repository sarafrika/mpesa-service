package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A till whose store number is still its till number asks someone to confirm it, within the Overview's scope. */
class StoreNumberAttentionSourceTest {

    private final MpesaShortCodeRepository repository = mock(MpesaShortCodeRepository.class);
    private final StoreNumberAttentionSource source = new StoreNumberAttentionSource(repository);

    @Test
    void anUnconfirmedTillIsListedOnlyInItsScope() {
        MpesaShortCode till = new MpesaShortCode();
        till.setUuid(UUID.randomUUID());
        till.setShortcode("5123456");
        till.setStoreNumber("5123456");
        till.setBusinessName("Shop");
        till.setShortcodeType(ShortcodeType.TILL);
        till.setEnvironment(Environment.PRODUCTION);
        when(repository.findTillsWithUnconfirmedStoreNumber()).thenReturn(List.of(till));

        List<AttentionItemDto> items = source.currentItems(AttentionScope.ALL);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(StoreNumberAttentionSource.KIND);
            assertThat(item.title()).contains("5123456");
            assertThat(item.shortcodeUuid()).isEqualTo(till.getUuid());
        });
        assertThat(source.currentItems(new AttentionScope(null, Environment.SANDBOX))).isEmpty();
        assertThat(source.currentItems(new AttentionScope(UUID.randomUUID(), null))).isEmpty();
    }
}
