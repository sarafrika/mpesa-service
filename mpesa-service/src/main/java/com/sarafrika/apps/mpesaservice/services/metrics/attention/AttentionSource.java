package com.sarafrika.apps.mpesaservice.services.metrics.attention;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import java.util.List;

/** One kind of "Needs attention" item on the Overview. Implement it as a Spring bean (failed callbacks,
 * approvals waiting for a checker...) and AttentionService picks it up; a source that throws is skipped. */
public interface AttentionSource {

    /** Items that apply now within the scope; runs on every Overview load, so keep it to cheap queries. */
    List<AttentionItemDto> currentItems(AttentionScope scope);
}
