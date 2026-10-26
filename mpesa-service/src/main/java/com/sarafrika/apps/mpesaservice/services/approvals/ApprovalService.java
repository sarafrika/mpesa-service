package com.sarafrika.apps.mpesaservice.services.approvals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.ApprovalDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.ApprovalSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PreCheckDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.ReversalDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.MakerCheckerException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.ApprovalRequest;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.ApprovalRequestRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.PayoutBatchRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReversalRequestRepository;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.utils.enums.ApprovalStatus;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** The maker-checker queue. A submission waits for a checker who is not its maker until it expires
 * (mpesa.approvals.expire-after); an approval hands it to ApprovalRunner. Submitters may put "title",
 * "daraja_call" and "pre_checks" ([{state, text}]) in the payload; they are shown to the checker. */
@Slf4j
@Service
public class ApprovalService implements ApprovalGateway {

    public static final String ENTITY = "APPROVAL";
    static final String AUDIT_SUBMITTED = "APPROVAL_SUBMITTED";
    static final String AUDIT_APPROVED = "APPROVAL_APPROVED";
    static final String AUDIT_REJECTED = "APPROVAL_REJECTED";
    static final String AUDIT_EXPIRED = "APPROVAL_EXPIRED";
    static final String OWN_REQUEST = "You raised this request, so another finance user has to decide it.";
    static final String NO_SUBJECT = "Your sign-in does not say who you are (no subject), so it cannot raise or "
            + "decide a money request. Sign in again.";

    private final ApprovalRequestRepository repository;
    private final MpesaShortCodeRepository shortcodes;
    private final ReversalRequestRepository reversals;
    private final PayoutBatchRepository batches;
    private final ObjectProvider<ApprovalLifecycle> lifecycles;
    private final ApprovalRunner runner;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final ApprovalProperties properties;
    private final TransactionTemplate tx;

    public ApprovalService(ApprovalRequestRepository repository, MpesaShortCodeRepository shortcodes,
                           ReversalRequestRepository reversals, PayoutBatchRepository batches,
                           ObjectProvider<ApprovalLifecycle> lifecycles, ApprovalRunner runner, AuditService audit,
                           ObjectMapper mapper, ApprovalProperties properties,
                           PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.shortcodes = shortcodes;
        this.reversals = reversals;
        this.batches = batches;
        this.lifecycles = lifecycles;
        this.runner = runner;
        this.audit = audit;
        this.mapper = mapper;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public UUID submit(ApprovalSubmission submission) {
        if (submission == null || !StringUtils.hasText(submission.type()) || !runner.knows(submission.type())) {
            throw new InvalidRequestException("type", "is not a request type this service can run");
        }
        if (submission.amount() == null || submission.amount().signum() <= 0) {
            throw new InvalidRequestException("amount", "must be more than 0");
        }
        if (!StringUtils.hasText(submission.reason())) {
            throw new InvalidRequestException("reason", "is required");
        }
        if (submission.reason().length() > 1000) {
            throw new InvalidRequestException("reason", "must be at most 1000 characters");
        }
        String makerSub = CurrentActor.subject();
        if (makerSub == null) {
            throw new MakerCheckerException(NO_SUBJECT);
        }
        if (submission.shortcodeUuid() != null && shortcodes.findByUuid(submission.shortcodeUuid()).isEmpty()) {
            throw new NotFoundException("Shortcode not found");
        }
        ObjectNode payload = submission.payload() instanceof ObjectNode object ? object.deepCopy()
                : mapper.createObjectNode();
        JsonNode title = payload.remove("title");
        JsonNode darajaCall = payload.remove("daraja_call");
        JsonNode preChecks = payload.remove("pre_checks");

        ApprovalRequest request = new ApprovalRequest();
        request.setType(submission.type());
        request.setTitle(clip(title != null && title.isTextual() && !title.asText().isBlank() ? title.asText()
                : ApprovalTypes.label(submission.type()) + " of KES " + money(submission.amount()), 200));
        request.setDarajaCall(darajaCall != null && darajaCall.isTextual() ? clip(darajaCall.asText(), 200) : null);
        request.setShortcodeUuid(submission.shortcodeUuid());
        request.setAmount(submission.amount());
        request.setReason(submission.reason().trim());
        request.setPayload(payload.toString());
        request.setPreChecks(mapper.valueToTree(readChecks(preChecks)).toString());
        request.setMaker(CurrentActor.name());
        request.setMakerSub(makerSub);
        request.setMakerName(actorName());
        request.setExpiresAt(Instant.now().plus(properties.expireAfter()));
        ApprovalRequest saved = repository.saveAndFlush(request);
        audit.record(AUDIT_SUBMITTED, ENTITY, saved.getUuid().toString(), null, ApprovalRunner.snapshot(saved));
        return saved.getUuid();
    }

    /** Statuses and type are optional filters; several statuses match any of them. */
    public record Filter(Collection<ApprovalStatus> statuses, String type, UUID shortcodeUuid) {
    }

    @Transactional(readOnly = true)
    public Page<ApprovalDto> list(Filter filter, Pageable pageable) {
        Page<ApprovalRequest> page = repository.findAll(matching(filter), pageable);
        Map<UUID, MpesaShortCode> codes = new HashMap<>();
        Me me = Me.current();
        return page.map(request -> view(request, code(codes, request.getShortcodeUuid()), me, false));
    }

    @Transactional(readOnly = true)
    public ApprovalDto get(UUID uuid) {
        ApprovalRequest request = repository.findByUuid(uuid).orElseThrow(() -> notFound());
        return view(request, code(new HashMap<>(), request.getShortcodeUuid()), Me.current(), true);
    }

    @Transactional(readOnly = true)
    public ApprovalSummaryDto summary() {
        return new ApprovalSummaryDto(repository.countByStatus(ApprovalStatus.WAITING),
                CurrentActor.subject() == null ? 0
                        : repository.countByStatusAndMakerSub(ApprovalStatus.WAITING, CurrentActor.subject()));
    }

    /** Approves as the signed-in checker and starts the run once committed. */
    public ApprovalDto approve(UUID uuid, String note) {
        decide(uuid, ApprovalStatus.APPROVED, note);
        return get(uuid);
    }

    public ApprovalDto reject(UUID uuid, String note) {
        if (!StringUtils.hasText(note)) {
            throw new InvalidRequestException("note", "is required to reject");
        }
        decide(uuid, ApprovalStatus.REJECTED, note);
        return get(uuid);
    }

    private void decide(UUID uuid, ApprovalStatus decision, String note) {
        String me = CurrentActor.name();
        String mySub = CurrentActor.subject();
        String myName = actorName();
        if (mySub == null) {
            throw new MakerCheckerException(NO_SUBJECT);
        }
        Boolean expired = tx.execute(status -> {
            ApprovalRequest request = repository.lockByUuid(uuid).orElseThrow(() -> notFound());
            if (request.getStatus() != ApprovalStatus.WAITING) {
                throw new ConflictException("This request was already " + word(request.getStatus())
                        + (request.getChecker() != null ? " by " + request.getChecker() : "") + ".");
            }
            if (!request.getExpiresAt().isAfter(Instant.now())) {
                expire(request);
                return true;
            }
            if (new Me(me, mySub).made(request)) {
                throw new MakerCheckerException(OWN_REQUEST);
            }
            Map<String, Object> before = ApprovalRunner.snapshot(request);
            request.setStatus(decision);
            request.setChecker(me);
            request.setCheckerSub(mySub);
            request.setCheckerName(myName);
            request.setDecisionNote(StringUtils.hasText(note) ? note.trim() : null);
            request.setDecidedAt(Instant.now());
            repository.saveAndFlush(request);
            if (decision == ApprovalStatus.APPROVED) {
                audit.record(AUDIT_APPROVED, ENTITY, uuid.toString(), before, ApprovalRunner.snapshot(request));
                runner.dispatchAfterCommit(uuid);
            } else {
                closed(request);
                audit.record(AUDIT_REJECTED, ENTITY, uuid.toString(), before, ApprovalRunner.snapshot(request));
            }
            return false;
        });
        if (Boolean.TRUE.equals(expired)) {
            throw new ConflictException("This request expired before it was decided; raise it again if it is "
                    + "still needed.");
        }
    }

    /** Marks waiting requests past their expiry as EXPIRED; returns how many. */
    public int expireDue() {
        int count = 0;
        List<UUID> due = repository.findDue(ApprovalStatus.WAITING, Instant.now(), PageRequest.of(0, 100));
        for (UUID uuid : due) {
            Boolean done = tx.execute(status -> repository.lockByUuid(uuid)
                    .filter(r -> r.getStatus() == ApprovalStatus.WAITING && !r.getExpiresAt().isAfter(Instant.now()))
                    .map(r -> {
                        expire(r);
                        return true;
                    }).orElse(false));
            count += Boolean.TRUE.equals(done) ? 1 : 0;
        }
        return count;
    }

    private void expire(ApprovalRequest request) {
        Map<String, Object> before = ApprovalRunner.snapshot(request);
        request.setStatus(ApprovalStatus.EXPIRED);
        request.setOutcomeMessage("Nobody decided it before " + request.getExpiresAt() + ".");
        repository.saveAndFlush(request);
        closed(request);
        audit.record(AUDIT_EXPIRED, ENTITY, request.getUuid().toString(), before, ApprovalRunner.snapshot(request));
    }

    private void closed(ApprovalRequest request) {
        lifecycles.orderedStream().filter(l -> l.type().equals(request.getType()))
                .forEach(l -> l.closed(request.getUuid(), request.getStatus()));
    }

    /** The signed-in person. The same subject (a username can change) or the same username both count as
     * the maker; rows from before subjects were stored have only the username. */
    record Me(String name, String sub) {
        static Me current() {
            return new Me(CurrentActor.name(), CurrentActor.subject());
        }

        boolean made(ApprovalRequest request) {
            return (request.getMakerSub() != null && request.getMakerSub().equals(sub))
                    || request.getMaker().equals(name);
        }
    }

    private ApprovalDto view(ApprovalRequest r, MpesaShortCode code, Me me, boolean detail) {
        JsonNode payload = read(r.getPayload());
        UUID transactionUuid = uuid(payload.path("transaction_uuid"));
        UUID batchUuid = null;
        ReversalDto reversal = null;
        if (detail && ApprovalTypes.BULK_PAYOUT.equals(r.getType())) {
            batchUuid = batches.findByApprovalUuid(r.getUuid()).map(b -> b.getUuid()).orElse(null);
        }
        if (detail && ApprovalTypes.REVERSAL.equals(r.getType())) {
            reversal = reversals.findByApprovalUuid(r.getUuid()).map(v -> new ReversalDto(v.getUuid(),
                    v.getPaymentUuid(), v.getTransactionId(), v.getAmount(), v.getOriginalAmount(),
                    v.getStatus().name(), v.getResultCode(), v.getResultDesc(), v.getReversalTransactionId(),
                    offset(v.getSentAt()), offset(v.getCompletedAt()), v.getClosedBy(), v.getCloseNote()))
                    .orElse(null);
        }
        return new ApprovalDto(r.getUuid(), r.getType(), r.getStatus().name(), r.getTitle(), r.getShortcodeUuid(),
                code == null ? null : code.getShortcode(), code == null ? null : code.getBusinessName(),
                r.getAmount(), r.getReason(), r.getDarajaCall(), readChecks(read(r.getPreChecks())), r.getMaker(),
                r.getMakerName(), r.getChecker(), r.getCheckerName(), r.getDecisionNote(), offset(r.getDecidedAt()),
                offset(r.getExpiresAt()), offset(r.getExecutedAt()), r.getOutcomeMessage(), offset(r.getCreatedAt()),
                me.made(r), transactionUuid, batchUuid, reversal,
                !detail ? null : PersonalData.visible() ? payload : PersonalData.redact(payload));
    }

    private MpesaShortCode code(Map<UUID, MpesaShortCode> cache, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return cache.computeIfAbsent(uuid, key -> shortcodes.findByUuid(key).orElse(null));
    }

    private static Specification<ApprovalRequest> matching(Filter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (StringUtils.hasText(filter.type())) {
                predicates.add(cb.equal(root.get("type"), filter.type().trim()));
            }
            if (filter.shortcodeUuid() != null) {
                predicates.add(cb.equal(root.get("shortcodeUuid"), filter.shortcodeUuid()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<PreCheckDto> readChecks(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        try {
            List<PreCheckDto> checks = mapper.convertValue(node, new TypeReference<List<PreCheckDto>>() {
            });
            return checks.stream().filter(c -> c != null && StringUtils.hasText(c.text()))
                    .map(c -> new PreCheckDto(state(c.state()), c.text())).toList();
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring unreadable pre-checks: {}", ex.getMessage());
            return List.of();
        }
    }

    private static String state(String state) {
        String upper = state == null ? "" : state.trim().toUpperCase(Locale.ROOT);
        return List.of(PreCheckDto.OK, PreCheckDto.WARN, PreCheckDto.FAIL).contains(upper) ? upper : PreCheckDto.WARN;
    }

    private JsonNode read(String json) {
        try {
            return json == null ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (Exception ex) {
            return mapper.createObjectNode();
        }
    }

    private static UUID uuid(JsonNode node) {
        try {
            return node.isTextual() ? UUID.fromString(node.asText()) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String word(ApprovalStatus status) {
        return switch (status) {
            case WAITING -> "waiting";
            case APPROVED, EXECUTING, EXECUTED, FAILED -> "approved";
            case REJECTED -> "rejected";
            case EXPIRED -> "expired";
        };
    }

    static String actorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String name = jwt.getToken().getClaimAsString("name");
            return StringUtils.hasText(name) ? clip(name, 255) : null;
        }
        return null;
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Approval request not found");
    }
}
