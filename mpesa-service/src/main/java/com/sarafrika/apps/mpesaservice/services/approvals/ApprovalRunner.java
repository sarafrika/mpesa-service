package com.sarafrika.apps.mpesaservice.services.approvals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.models.ApprovalRequest;
import com.sarafrika.apps.mpesaservice.repositories.ApprovalRequestRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs approved requests through the executor of their type. A request is claimed under a row lock
 * (APPROVED to EXECUTING), so it runs once; one left EXECUTING by a crash is resumed when its executor is
 * resumable and otherwise marked FAILED with its outcome unknown. */
@Slf4j
@Component
public class ApprovalRunner {

    static final String AUDIT_EXECUTED = "APPROVAL_EXECUTED";
    static final String AUDIT_FAILED = "APPROVAL_EXECUTION_FAILED";
    static final String INTERRUPTED = "The run stopped before it finished, so Safaricom may have received the "
            + "request. Check the transactions before raising it again.";

    private final ApprovalRequestRepository repository;
    private final ObjectProvider<ApprovalExecutor> executorBeans;
    private volatile Map<String, ApprovalExecutor> executors;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final ApprovalProperties properties;
    private final TaskExecutor pool;
    private final TransactionTemplate tx;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public ApprovalRunner(ApprovalRequestRepository repository, ObjectProvider<ApprovalExecutor> executors,
                          AuditService audit,
                          ObjectMapper mapper, ApprovalProperties properties,
                          @Qualifier("approvalTaskExecutor") TaskExecutor pool,
                          PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.executorBeans = executors;
        this.audit = audit;
        this.mapper = mapper;
        this.properties = properties;
        this.pool = pool;
        this.tx = new TransactionTemplate(transactionManager);
        // Runs from afterCommit callbacks too, where the finished transaction is still bound.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean knows(String type) {
        return executors().containsKey(type);
    }

    // Looked up on first use: an executor may depend on a service that submits through the gateway.
    private Map<String, ApprovalExecutor> executors() {
        Map<String, ApprovalExecutor> known = executors;
        if (known == null) {
            known = executorBeans.orderedStream()
                    .collect(Collectors.toMap(ApprovalExecutor::type, Function.identity()));
            executors = known;
        }
        return known;
    }

    /** Starts the run once the approving transaction has committed; the resume job catches any miss. */
    public void dispatchAfterCommit(UUID uuid) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(uuid);
                }
            });
        } else {
            dispatch(uuid);
        }
    }

    private void dispatch(UUID uuid) {
        try {
            pool.execute(() -> run(uuid));
        } catch (TaskRejectedException ex) {
            log.warn("Approval {} not started now ({}); the resume job will pick it up", uuid, ex.getMessage());
        }
    }

    /** Claims and runs one request; does nothing if it is not APPROVED (or resumable) any more. */
    public void run(UUID uuid) {
        if (!running.add(uuid)) {
            return;
        }
        try {
            ApprovalExecutor.Approved claimed = tx.execute(status -> claim(uuid));
            if (claimed == null) {
                return;
            }
            ApprovalExecutor.Outcome outcome;
            try {
                outcome = executors().get(claimed.type()).execute(claimed);
            } catch (RuntimeException ex) {
                log.error("Approval {} ({}) failed while running", uuid, claimed.type(), ex);
                outcome = new ApprovalExecutor.Outcome(false, "Stopped with an error: " + ex.getMessage());
            }
            ApprovalExecutor.Outcome result = outcome;
            tx.executeWithoutResult(status -> finish(uuid, result));
        } catch (RuntimeException ex) {
            log.error("Approval {} could not be run; the resume job will try again", uuid, ex);
        } finally {
            running.remove(uuid);
        }
    }

    /** Requests approved but never started, and executions whose heartbeat went quiet. */
    public void resumeDue() {
        Instant now = Instant.now();
        List<UUID> due = repository.findResumable(now.minusSeconds(60), now.minus(properties.executionLease()),
                PageRequest.of(0, 20));
        due.forEach(this::run);
    }

    private ApprovalExecutor.Approved claim(UUID uuid) {
        ApprovalRequest request = repository.lockByUuid(uuid).orElse(null);
        if (request == null) {
            return null;
        }
        ApprovalExecutor executor = executors().get(request.getType());
        Instant now = Instant.now();
        if (request.getStatus() == ApprovalStatus.APPROVED) {
            if (executor == null) {
                fail(request, "Nothing can run a " + request.getType() + " request in this service");
                return null;
            }
            request.setStatus(ApprovalStatus.EXECUTING);
        } else if (request.getStatus() == ApprovalStatus.EXECUTING && stale(request, now)) {
            if (!(executor instanceof ResumableExecutor)) {
                fail(request, INTERRUPTED);
                return null;
            }
            log.warn("Resuming approval {} ({}) after its run went quiet", uuid, request.getType());
        } else {
            return null;
        }
        request.setHeartbeatAt(now);
        repository.save(request);
        return new ApprovalExecutor.Approved(request.getUuid(), request.getType(), request.getShortcodeUuid(),
                request.getAmount(), payload(request), request.getMaker(), request.getChecker());
    }

    private boolean stale(ApprovalRequest request, Instant now) {
        return request.getHeartbeatAt() == null
                || request.getHeartbeatAt().isBefore(now.minus(properties.executionLease()));
    }

    private void finish(UUID uuid, ApprovalExecutor.Outcome outcome) {
        ApprovalRequest request = repository.lockByUuid(uuid).orElseThrow();
        if (request.getStatus() != ApprovalStatus.EXECUTING) {
            return;
        }
        request.setStatus(outcome.sent() ? ApprovalStatus.EXECUTED : ApprovalStatus.FAILED);
        request.setExecutedAt(Instant.now());
        request.setHeartbeatAt(null);
        // A callback may already have written Safaricom's answer; that one is newer.
        if (request.getOutcomeMessage() == null) {
            request.setOutcomeMessage(truncate(outcome.message()));
        }
        repository.save(request);
        audit.record(outcome.sent() ? AUDIT_EXECUTED : AUDIT_FAILED, ApprovalService.ENTITY, uuid.toString(),
                Map.of("status", ApprovalStatus.EXECUTING.name()), snapshot(request));
    }

    private void fail(ApprovalRequest request, String message) {
        request.setStatus(ApprovalStatus.FAILED);
        request.setExecutedAt(Instant.now());
        request.setHeartbeatAt(null);
        request.setOutcomeMessage(truncate(message));
        repository.save(request);
        audit.record(AUDIT_FAILED, ApprovalService.ENTITY, request.getUuid().toString(), null, snapshot(request));
    }

    static Map<String, Object> snapshot(ApprovalRequest request) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", request.getType());
        view.put("status", request.getStatus().name());
        view.put("amount", request.getAmount());
        view.put("maker", request.getMaker());
        view.put("checker", request.getChecker());
        view.put("outcome_message", request.getOutcomeMessage());
        return view;
    }

    private JsonNode payload(ApprovalRequest request) {
        try {
            return mapper.readTree(request.getPayload());
        } catch (Exception ex) {
            return mapper.createObjectNode();
        }
    }

    static String truncate(String message) {
        return message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
