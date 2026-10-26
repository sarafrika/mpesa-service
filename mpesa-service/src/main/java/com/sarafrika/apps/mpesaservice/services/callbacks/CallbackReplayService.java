package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDetailDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.models.CallbackInboxEntry;
import com.sarafrika.apps.mpesaservice.repositories.CallbackInboxRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackAcks.Processing;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Runs a failed callback's stored body through its handler again. Handlers settle a payment once per TransID,
 * CheckoutRequestID or conversation id, so a replay of something already applied changes nothing; the claim
 * below also keeps two operators from replaying one entry at the same time. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackReplayService {

    static final String AUDIT_ACTION = "CALLBACK_REPLAYED";

    private final CallbackInboxRepository repository;
    private final CallbackInboxService inbox;
    private final CallbackDispatcher dispatcher;
    private final CallbackInboxRecorder recorder;
    private final AuditService auditService;

    public CallbackDetailDto replay(UUID uuid) {
        CallbackInboxEntry entry = inbox.find(uuid);
        String blocked = inbox.replayBlockedReason(entry);
        if (blocked != null) {
            throw new ConflictException(blocked);
        }
        Map<String, Object> before = Map.of("status", entry.getStatus().name(), "attempts", entry.getAttempts());
        if (repository.claimForReplay(uuid, inbox.now(), inbox.stuckBefore()) == 0) {
            throw new ConflictException("Someone else is replaying this callback, or it no longer needs it.");
        }

        Processing processing;
        try {
            processing = dispatcher.dispatch(entry.getEndpoint(), entry.getBody());
        } catch (ConflictException e) {
            processing = Processing.failure(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Replay of callback {} failed", uuid, e);
            processing = Processing.failure(CallbackOutcome.describe(e));
        }

        CallbackInboxEntry replayed = inbox.find(uuid);
        replayed.setStatus(processing.ok() ? CallbackStatus.REPLAYED : CallbackStatus.FAILED);
        replayed.setError(processing.ok() ? null : processing.error());
        recorder.link(replayed);
        CallbackInboxEntry saved = repository.save(replayed);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", saved.getStatus().name());
        after.put("attempts", saved.getAttempts());
        after.put("error", saved.getError());
        after.put("endpoint", saved.getEndpoint());
        auditService.record(AUDIT_ACTION, "CALLBACK", uuid.toString(), before, after);
        return inbox.detail(saved);
    }
}
