package com.sarafrika.apps.mpesaservice.services.approvals.reversal;

import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TimelineEventDto;
import com.sarafrika.apps.mpesaservice.models.ReversalRequest;
import com.sarafrika.apps.mpesaservice.repositories.ReversalRequestRepository;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionTimelineContributor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Shows each reversal of a received payment on its timeline: requested, sent, and Safaricom's answer. */
@Component
@RequiredArgsConstructor
public class ReversalTimelineContributor implements TransactionTimelineContributor {

    static final String KIND = "reversal";

    private final ReversalRequestRepository reversals;

    @Override
    @Transactional(readOnly = true)
    public List<TimelineEventDto> eventsFor(UUID paymentUuid) {
        List<TimelineEventDto> events = new ArrayList<>();
        for (ReversalRequest r : reversals.findByPaymentUuidOrderByCreatedAtAscIdAsc(paymentUuid)) {
            events.add(new TimelineEventDto(KIND, "Reversal of KES " + ReversalService.money(r.getAmount())
                    + " requested", "By " + r.getRequestedBy() + "; waits for a checker", at(r.getCreatedAt()), "info"));
            if (r.getSentAt() != null) {
                events.add(new TimelineEventDto(KIND, "Reversal sent to Safaricom", null, at(r.getSentAt()), "info"));
            }
            switch (r.getStatus()) {
                case REVERSED -> events.add(new TimelineEventDto(KIND, "Reversed", r.getReversalTransactionId() == null
                        ? r.getResultDesc() : "Receipt " + r.getReversalTransactionId(), at(r.getCompletedAt()), "success"));
                case FAILED -> events.add(new TimelineEventDto(KIND, "Reversal failed", r.getResultDesc(),
                        at(r.getCompletedAt() != null ? r.getCompletedAt() : r.getUpdatedAt()), "danger"));
                case OUTCOME_UNKNOWN -> events.add(new TimelineEventDto(KIND, "Reversal outcome unknown",
                        r.getResultDesc(), at(r.getTimedOutAt() != null ? r.getTimedOutAt() : r.getUpdatedAt()),
                        "warning"));
                case CANCELLED -> events.add(new TimelineEventDto(KIND, "Reversal not approved", r.getResultDesc(),
                        at(r.getUpdatedAt()), "muted"));
                default -> {
                }
            }
        }
        return events;
    }

    private static OffsetDateTime at(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
