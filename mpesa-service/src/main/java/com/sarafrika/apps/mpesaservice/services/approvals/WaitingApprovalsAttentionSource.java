package com.sarafrika.apps.mpesaservice.services.approvals;

import com.sarafrika.apps.mpesaservice.dtos.admin.metrics.AttentionItemDto;
import com.sarafrika.apps.mpesaservice.models.ApprovalRequest;
import com.sarafrika.apps.mpesaservice.repositories.ApprovalRequestRepository;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionScope;
import com.sarafrika.apps.mpesaservice.services.metrics.attention.AttentionSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Reversals, bulk payouts and sweeps waiting for a checker; ignores the environment filter. */
@Component
@Order(30)
@RequiredArgsConstructor
public class WaitingApprovalsAttentionSource implements AttentionSource {

    private final ApprovalRequestRepository repository;

    @Override
    public List<AttentionItemDto> currentItems(AttentionScope scope) {
        List<ApprovalRequest> waiting = repository.findWaiting().stream()
                .filter(r -> scope.shortcodeUuid() == null || scope.shortcodeUuid().equals(r.getShortcodeUuid()))
                .toList();
        if (waiting.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        ApprovalRequest oldest = waiting.get(0);
        ApprovalRequest soonest = waiting.stream().min((a, b) -> a.getExpiresAt().compareTo(b.getExpiresAt()))
                .orElse(oldest);
        String title = waiting.size() == 1 ? "1 approval waiting for a checker"
                : waiting.size() + " approvals waiting for a checker";
        String description = "The oldest was raised " + span(Duration.between(oldest.getCreatedAt(), now))
                + " ago; the next expires in " + span(Duration.between(now, soonest.getExpiresAt())) + ".";
        return List.of(new AttentionItemDto("APPROVALS_WAITING", AttentionItemDto.INFO, title, description,
                "/approvals", scope.shortcodeUuid()));
    }

    static String span(Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        return hours < 48 ? hours + " h" : hours / 24 + " days";
    }
}
