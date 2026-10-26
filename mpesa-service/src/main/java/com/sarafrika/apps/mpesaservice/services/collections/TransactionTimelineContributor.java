package com.sarafrika.apps.mpesaservice.services.collections;

import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TimelineEventDto;
import java.util.List;
import java.util.UUID;

/** Adds steps to a transaction's timeline from elsewhere, e.g. webhook deliveries to the owning product. */
public interface TransactionTimelineContributor {

    List<TimelineEventDto> eventsFor(UUID paymentUuid);
}
