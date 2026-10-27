package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackSummaryDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.CallbackInboxEntry;
import com.sarafrika.apps.mpesaservice.repositories.CallbackInboxRepository;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Reads the callback inbox for the console: a filtered page, one entry (masked by role) and 24 h counts. */
@Service
public class CallbackInboxService {

    /** Results finance can attach by hand; a B2B Express Checkout callback is matched on our own requestId. */
    static final java.util.Set<CallbackKind> ATTACHABLE_KINDS = java.util.EnumSet.of(CallbackKind.B2B_RESULT,
            CallbackKind.REVERSAL_RESULT, CallbackKind.TAX_REMITTANCE_RESULT, CallbackKind.POCHI_RESULT);

    static final Duration SUMMARY_WINDOW = Duration.ofHours(24);

    private final CallbackInboxRepository repository;
    private final CallbackInboxProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CallbackInboxService(CallbackInboxRepository repository, CallbackInboxProperties properties,
                                ObjectMapper objectMapper, ObjectProvider<Clock> clock) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock.getIfUnique(Clock::systemUTC);
    }

    /** Optional filters; q matches the receipts and ids found in the body (case-insensitive substring). */
    public record Filter(String status, String kind, String endpoint, String q, OffsetDateTime from,
                         OffsetDateTime to) {
    }

    @Transactional(readOnly = true)
    public Page<CallbackDto> list(Filter filter, Pageable pageable) {
        return repository.findAll(matching(filter), pageable).map(CallbackInboxService::toDto);
    }

    private static final java.util.regex.Pattern PHONE_LIKE = java.util.regex.Pattern.compile(
            "(?<![A-Za-z0-9_])\\+?\\d{9,}(?![A-Za-z0-9_])");

    /** The ids line with phone-like values (a BillRefNumber is often one) masked unless finance or admin; only
     * whole digit tokens, so a checkout id such as ws_CO_191220191020363925 stays readable. */
    static String reference(String reference) {
        if (reference == null || PersonalData.visible()) {
            return reference;
        }
        return PHONE_LIKE.matcher(reference).replaceAll(match -> PersonalData.maskPhone(match.group()));
    }

    @Transactional(readOnly = true)
    public CallbackDetailDto get(UUID uuid) {
        return detail(find(uuid));
    }

    @Transactional(readOnly = true)
    public CallbackSummaryDto summary() {
        Instant since = clock.instant().minus(SUMMARY_WINDOW);
        Map<CallbackStatus, Long> counts = new EnumMap<>(CallbackStatus.class);
        for (Object[] row : repository.countByStatusSince(since)) {
            counts.put((CallbackStatus) row[0], ((Number) row[1]).longValue());
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new CallbackSummaryDto(offset(since), total, counts.getOrDefault(CallbackStatus.RECEIVED, 0L),
                counts.getOrDefault(CallbackStatus.PROCESSED, 0L), counts.getOrDefault(CallbackStatus.FAILED, 0L),
                counts.getOrDefault(CallbackStatus.REPLAYED, 0L), counts.getOrDefault(CallbackStatus.REJECTED, 0L));
    }

    CallbackInboxEntry find(UUID uuid) {
        return repository.findByUuid(uuid).orElseThrow(() -> new NotFoundException("No callback " + uuid));
    }

    CallbackDetailDto detail(CallbackInboxEntry entry) {
        String blocked = replayBlockedReason(entry);
        return new CallbackDetailDto(toDto(entry), headers(entry), body(entry), entry.getBodySize(),
                entry.isBodyTruncated(), blocked == null, blocked, attachable(entry));
    }

    /** Null when an operator may replay the entry now, otherwise why not. */
    String replayBlockedReason(CallbackInboxEntry entry) {
        if (entry.getKind() == CallbackKind.C2B_VALIDATION) {
            return "A validation answer went back to Safaricom at the time; replaying it cannot change the decision.";
        }
        if (entry.isBodyTruncated() || entry.getBody() == null || !entry.getEndpoint().startsWith(CallbackKind.PREFIX)
                || entry.getBody().contains("\"" + CallbackInboxRecorder.UNPARSED + "\"")) {
            return "The body was not stored as JSON, so there is nothing to replay.";
        }
        if (entry.getStatus() == CallbackStatus.REJECTED) {
            return "It was refused before any handler ran (an unknown callback URL), so there is nothing to replay.";
        }
        if (entry.getStatus() == CallbackStatus.FAILED) {
            return null;
        }
        if (entry.getStatus() == CallbackStatus.RECEIVED) {
            Instant last = entry.getLastAttemptAt() != null ? entry.getLastAttemptAt() : entry.getReceivedAt();
            return last.isBefore(stuckBefore()) ? null : "It is still being handled.";
        }
        return "Only failed callbacks can be replayed; this one was handled.";
    }

    /** A failed B2B or reversal result with a JSON body: finance may attach it to what it settles. */
    static boolean attachable(CallbackInboxEntry entry) {
        return entry.getStatus() == CallbackStatus.FAILED
                && ATTACHABLE_KINDS.contains(entry.getKind())
                && entry.getBody() != null && !entry.isBodyTruncated()
                && !entry.getBody().contains("\"" + CallbackInboxRecorder.UNPARSED + "\"");
    }

    Instant stuckBefore() {
        return clock.instant().minus(properties.stuckAfter());
    }

    Instant now() {
        return clock.instant();
    }

    private JsonNode body(CallbackInboxEntry entry) {
        if (entry.getBody() == null) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.readTree(entry.getBody());
            return PersonalData.visible() ? tree : PersonalData.redact(tree);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> headers(CallbackInboxEntry entry) {
        if (entry.getHeaders() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(entry.getHeaders(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    static CallbackDto toDto(CallbackInboxEntry entry) {
        return new CallbackDto(entry.getUuid(), entry.getEndpoint(), entry.getKind().name(), entry.getSourceIp(),
                entry.getStatus().name(), entry.getError(), entry.getAttempts(), offset(entry.getLastAttemptAt()),
                reference(entry.getReference()), entry.getPaymentUuid(), offset(entry.getReceivedAt()));
    }

    static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static Specification<CallbackInboxEntry> matching(Filter filter) {
        CallbackStatus status = parse(CallbackStatus.class, "status", filter.status());
        CallbackKind kind = parse(CallbackKind.class, "kind", filter.kind());
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) {
            throw new InvalidRequestException("to", "must be after from");
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (kind != null) {
                predicates.add(cb.equal(root.get("kind"), kind));
            }
            if (StringUtils.hasText(filter.endpoint())) {
                predicates.add(cb.equal(root.get("endpoint"), filter.endpoint().trim()));
            }
            if (StringUtils.hasText(filter.q())) {
                String like = "%" + filter.q().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.like(cb.lower(root.get("reference")), like, '\\'));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), filter.from().toInstant()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("receivedAt"), filter.to().toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String field, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(field, "unknown value '" + value + "'");
        }
    }
}
