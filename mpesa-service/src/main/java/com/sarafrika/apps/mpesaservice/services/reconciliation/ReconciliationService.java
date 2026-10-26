package com.sarafrika.apps.mpesaservice.services.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationExceptionDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationRunDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.ReconciliationSummaryDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.ReconciliationExceptionRecord;
import com.sarafrika.apps.mpesaservice.models.ReconciliationRun;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReconciliationExceptionRepository;
import com.sarafrika.apps.mpesaservice.repositories.ReconciliationRunRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationMatcher.Draft;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationAction;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionKind;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationRunStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationSource;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationTrigger;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Runs reconciliations (uploaded statement or Pull Transactions), stores their counts and exceptions, and lists
 * them. A new run over a period an earlier run covered supersedes that run's open exceptions. */
@Service
@Slf4j
public class ReconciliationService {

    static final String AUDIT_RUN = "RECONCILIATION_RUN";
    static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    // Safaricom: "Transactions can only be pulled within 48 hours" (developer.safaricom.co.ke/apis/PullTransaction FAQ).
    static final Duration PULL_WINDOW = Duration.ofHours(48);

    private final ReconciliationRunRepository runs;
    private final ReconciliationExceptionRepository exceptions;
    private final MpesaShortCodeRepository shortcodes;
    private final ReconciliationLedger ledger;
    private final PullTransactionsService pull;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final ReconciliationProperties properties;
    private final Clock clock;

    public ReconciliationService(ReconciliationRunRepository runs, ReconciliationExceptionRepository exceptions,
                                 MpesaShortCodeRepository shortcodes, ReconciliationLedger ledger,
                                 PullTransactionsService pull, AuditService audit, ObjectMapper objectMapper,
                                 ReconciliationProperties properties, Optional<Clock> clock) {
        this.runs = runs;
        this.exceptions = exceptions;
        this.shortcodes = shortcodes;
        this.ledger = ledger;
        this.pull = pull;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock.orElse(Clock.system(NAIROBI));
    }

    /** An uploaded org statement CSV for whole days from..to (inclusive, East Africa Time). */
    @Transactional
    public ReconciliationRunDto reconcileStatement(UUID shortcodeUuid, LocalDate from, LocalDate to, String fileName,
                                                   byte[] content) {
        MpesaShortCode shortcode = shortcode(shortcodeUuid);
        if (from.isAfter(to)) {
            throw new InvalidRequestException("to", "must be on or after the start date");
        }
        if (from.plusDays(properties.maxPeriodDays()).isBefore(to.plusDays(1))) {
            throw new InvalidRequestException("to", "the period may be at most " + properties.maxPeriodDays() + " days");
        }
        if (content.length > properties.maxFileSize().toBytes()) {
            throw new InvalidRequestException("file", "the file is larger than " + properties.maxFileSize().toMegabytes()
                    + " MB; upload a shorter period");
        }
        StatementCsvParser.ParsedStatement parsed = StatementCsvParser.parse(content, properties.maxRows());
        if (StringUtils.hasText(parsed.shortCode()) && !parsed.shortCode().equals(shortcode.getShortcode())) {
            throw new InvalidRequestException("file", "this statement is for shortcode " + parsed.shortCode()
                    + ", not " + shortcode.getShortcode());
        }
        return run(shortcode, ReconciliationSource.STATEMENT, ReconciliationTrigger.MANUAL, from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(), fileName, parsed.lines());
    }

    /** Safaricom's Pull Transactions rows for [start, end) as the statement; deposits only, as Pull is C2B only. */
    @Transactional
    public ReconciliationRunDto reconcilePull(UUID shortcodeUuid, LocalDateTime start, LocalDateTime end,
                                              ReconciliationTrigger trigger) {
        MpesaShortCode shortcode = shortcode(shortcodeUuid);
        pull.requireEnabled(shortcodeUuid);
        if (!end.isAfter(start)) {
            throw new InvalidRequestException("to", "must be after the start");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (start.isBefore(now.minus(PULL_WINDOW).minusMinutes(5))) {
            throw new InvalidRequestException("from", "Safaricom only returns Pull Transactions from the last 48 hours");
        }
        List<StatementLine> lines = pull.fetch(shortcode, start, end.isAfter(now) ? now : end);
        return run(shortcode, ReconciliationSource.PULL_TRANSACTIONS, trigger, start, end, null, lines);
    }

    @Transactional
    public ReconciliationRunDto run(MpesaShortCode shortcode, ReconciliationSource source, ReconciliationTrigger trigger,
                                    LocalDateTime start, LocalDateTime end, String fileName, List<StatementLine> lines) {
        boolean incomingOnly = source == ReconciliationSource.PULL_TRANSACTIONS;
        Set<String> receipts = new LinkedHashSet<>();
        lines.forEach(l -> receipts.add(l.receipt()));
        List<LedgerEntry> byReceipt = receipts.isEmpty() ? List.of()
                : ledger.byReceipts(shortcode.getUuid(), shortcode.getShortcode(), receipts, incomingOnly);
        List<LedgerEntry> inPeriod = ledger.settledInPeriod(shortcode.getUuid(), shortcode.getShortcode(), start, end,
                incomingOnly);
        ReconciliationMatcher.Result result = ReconciliationMatcher.match(lines, start, end, byReceipt, inPeriod,
                new ReconciliationMatcher.Markers(properties.chargeMarkers(), properties.reversalMarkers(),
                        properties.transferMarkers()));

        ReconciliationRun run = new ReconciliationRun();
        run.setShortcodeUuid(shortcode.getUuid());
        run.setSource(source);
        run.setTrigger(trigger);
        run.setStatus(ReconciliationRunStatus.COMPLETED);
        run.setPeriodStart(start);
        run.setPeriodEnd(end);
        run.setFileName(fileName == null ? null : truncate(fileName, 255));
        run.setStatementLines(result.statementLines());
        run.setSkippedLines(result.skippedLines());
        run.setChargeLines(result.chargeLines());
        run.setReversalLines(result.reversalLines());
        run.setLedgerRecords(result.ledgerRecords());
        run.setMatchedCount(result.matchedCount());
        run.setMatchedValue(result.matchedValue());
        run.setExceptionCount(result.exceptions().size());
        run.setExceptionValue(result.exceptionValue());
        run.setPaidInTotal(result.paidInTotal());
        run.setWithdrawnTotal(result.withdrawnTotal());
        run.setChargesTotal(result.chargesTotal());
        run.setCreatedBy(CurrentActor.name());
        run.setCompletedAt(LocalDateTime.now());
        ReconciliationRun saved = runs.save(run);

        exceptions.supersede(shortcode.getUuid(), saved.getId(), start, end);
        exceptions.saveAll(result.exceptions().stream().map(d -> record(saved, d)).toList());
        audit.record(AUDIT_RUN, "RECONCILIATION_RUN", saved.getUuid().toString(), null, Map.of(
                "shortcode", shortcode.getShortcode(), "source", source.name(), "trigger", trigger.name(),
                "period_start", start.toString(), "period_end", end.toString(),
                "matched", result.matchedCount(), "exceptions", result.exceptions().size()));
        return ReconciliationRunDto.from(saved, shortcode.getShortcode(), shortcode.getBusinessName(),
                result.exceptions().size());
    }

    /** A scheduled Pull run that could not reach Safaricom is kept as FAILED so the run list shows the gap. */
    @Transactional
    public void recordFailure(MpesaShortCode shortcode, ReconciliationSource source, LocalDateTime start,
                              LocalDateTime end, String reason) {
        ReconciliationRun run = new ReconciliationRun();
        run.setShortcodeUuid(shortcode.getUuid());
        run.setSource(source);
        run.setTrigger(ReconciliationTrigger.SCHEDULED);
        run.setStatus(ReconciliationRunStatus.FAILED);
        run.setPeriodStart(start);
        run.setPeriodEnd(end);
        run.setFailureReason(truncate(reason == null ? "Unknown failure" : reason, 1000));
        run.setCreatedBy(CurrentActor.SYSTEM);
        run.setCompletedAt(LocalDateTime.now());
        runs.save(run);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRunDto> listRuns(UUID shortcodeUuid, Pageable pageable) {
        Page<ReconciliationRun> page = shortcodeUuid == null ? runs.findAll(pageable)
                : runs.findByShortcodeUuid(shortcodeUuid, pageable);
        Map<Long, Long> open = new HashMap<>();
        if (!page.isEmpty()) {
            exceptions.countOpenByRunIds(page.map(ReconciliationRun::getId).toList())
                    .forEach(row -> open.put((Long) row[0], (Long) row[1]));
        }
        Map<UUID, MpesaShortCode> names = new HashMap<>();
        return page.map(run -> {
            MpesaShortCode code = names.computeIfAbsent(run.getShortcodeUuid(),
                    uuid -> shortcodes.findByUuid(uuid).orElse(null));
            return ReconciliationRunDto.from(run, code == null ? null : code.getShortcode(),
                    code == null ? null : code.getBusinessName(), open.getOrDefault(run.getId(), 0L));
        });
    }

    @Transactional(readOnly = true)
    public ReconciliationRunDto getRun(UUID uuid) {
        return dto(findRun(uuid));
    }

    @Transactional(readOnly = true)
    public ReconciliationSummaryDto summary(UUID uuid) {
        ReconciliationRun run = findRun(uuid);
        Map<ReconciliationExceptionKind, long[]> counts = new EnumMap<>(ReconciliationExceptionKind.class);
        Map<ReconciliationExceptionKind, BigDecimal> values = new EnumMap<>(ReconciliationExceptionKind.class);
        for (ReconciliationExceptionKind kind : ReconciliationExceptionKind.values()) {
            counts.put(kind, new long[2]);
            values.put(kind, BigDecimal.ZERO);
        }
        for (Object[] row : exceptions.summarise(run.getId())) {
            ReconciliationExceptionKind kind = (ReconciliationExceptionKind) row[0];
            long count = (Long) row[2];
            counts.get(kind)[0] += count;
            if (row[1] == ReconciliationExceptionStatus.OPEN) {
                counts.get(kind)[1] += count;
            }
            values.put(kind, values.get(kind).add((BigDecimal) row[3]));
        }
        List<ReconciliationSummaryDto.KindSummary> kinds = new ArrayList<>();
        for (ReconciliationExceptionKind kind : ReconciliationExceptionKind.values()) {
            kinds.add(new ReconciliationSummaryDto.KindSummary(kind.name(), counts.get(kind)[0], counts.get(kind)[1],
                    values.get(kind), kindFix(kind)));
        }
        return new ReconciliationSummaryDto(dto(run), kinds);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationExceptionDto> listExceptions(UUID runUuid, ReconciliationExceptionKind kind,
                                                           ReconciliationExceptionStatus status, Pageable pageable) {
        ReconciliationRun run = findRun(runUuid);
        Specification<ReconciliationExceptionRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("runId"), run.getId()));
            if (kind != null) {
                predicates.add(cb.equal(root.get("kind"), kind));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return exceptions.findAll(spec, pageable).map(e -> toDto(e, run.getUuid()));
    }

    /** The DTO with what a person may do now and the other party masked for non-finance roles. */
    public static ReconciliationExceptionDto toDto(ReconciliationExceptionRecord e, UUID runUuid) {
        String party = e.getOtherParty();
        if (party != null && !PersonalData.visible()) {
            int dash = party.indexOf(" - ");
            party = dash < 0 ? PersonalData.maskPhone(party)
                    : PersonalData.maskPhone(party.substring(0, dash)) + " - "
                    + PersonalData.shortName(party.substring(dash + 3));
        }
        return ReconciliationExceptionDto.from(e, runUuid, allowedActions(e).stream().map(Enum::name).toList(), party);
    }

    static List<ReconciliationAction> allowedActions(ReconciliationExceptionRecord e) {
        if (e.getStatus() != ReconciliationExceptionStatus.OPEN) {
            return List.of();
        }
        List<ReconciliationAction> actions = new ArrayList<>();
        boolean queryable = e.getLedgerPaymentUuid() != null && !"B2B_TRANSFER".equals(e.getLedgerType());
        switch (e.getKind()) {
            case MISSING_IN_LEDGER -> {
                if (e.getLedgerPaymentUuid() == null && ReconciliationExceptionRecord.IN.equals(e.getDirection())
                        && e.getReversedBy() == null && e.getStatementLine() != null) {
                    actions.add(ReconciliationAction.BACKFILL);
                }
                if (queryable) {
                    actions.add(ReconciliationAction.QUERY_STATUS);
                }
            }
            case MISSING_IN_STATEMENT, AMOUNT_MISMATCH -> {
                if (queryable) {
                    actions.add(ReconciliationAction.QUERY_STATUS);
                }
            }
            case DUPLICATE_IN_LEDGER -> actions.add(ReconciliationAction.MERGE_DUPLICATE);
        }
        actions.add(ReconciliationAction.MARK_RESOLVED);
        return actions;
    }

    static String kindFix(ReconciliationExceptionKind kind) {
        return switch (kind) {
            case MISSING_IN_LEDGER -> "On Safaricom's record but not ours; backfill deposits whose confirmation never "
                    + "arrived";
            case MISSING_IN_STATEMENT -> "In our ledger but not on Safaricom's record; query the status before the "
                    + "next run";
            case AMOUNT_MISMATCH -> "Amounts differ; compare the raw callback with the statement line";
            case DUPLICATE_IN_LEDGER -> "Same receipt saved more than once; merge and keep the first";
        };
    }

    ReconciliationRun findRun(UUID uuid) {
        return runs.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Reconciliation run not found"));
    }

    private ReconciliationRunDto dto(ReconciliationRun run) {
        MpesaShortCode code = shortcodes.findByUuid(run.getShortcodeUuid()).orElse(null);
        return ReconciliationRunDto.from(run, code == null ? null : code.getShortcode(),
                code == null ? null : code.getBusinessName(),
                exceptions.countByRunIdAndStatus(run.getId(), ReconciliationExceptionStatus.OPEN));
    }

    private ReconciliationExceptionRecord record(ReconciliationRun run, Draft draft) {
        ReconciliationExceptionRecord e = new ReconciliationExceptionRecord();
        e.setRunId(run.getId());
        e.setShortcodeUuid(run.getShortcodeUuid());
        e.setKind(draft.kind());
        e.setDirection(draft.direction());
        e.setReceipt(truncate(draft.receipt(), 50));
        e.setStatementAmount(draft.statementAmount());
        e.setLedgerAmount(draft.ledgerAmount());
        e.setLedgerCount(draft.ledger().size());
        if (!draft.ledger().isEmpty()) {
            LedgerEntry first = draft.ledger().getFirst();
            e.setLedgerPaymentUuid(first.uuid());
            e.setLedgerStatus(first.status());
            e.setLedgerType(first.type());
            e.setAccountReference(truncate(first.accountReference(), 100));
        }
        e.setReversedBy(draft.reversedBy());
        e.setSuggestedFix(draft.suggestedFix());
        StatementLine line = draft.line();
        if (line != null) {
            e.setStatementTime(line.completedAt());
            e.setDetails(truncate(line.details(), 500));
            e.setOtherParty(truncate(line.otherParty(), 255));
            String account = StatementBackfill.accountReference(line);
            if (account != null) {
                e.setAccountReference(truncate(account, 100));
            }
            e.setStatementLine(json(line));
        }
        return e;
    }

    private String json(StatementLine line) {
        try {
            return objectMapper.writeValueAsString(line);
        } catch (JsonProcessingException ex) {
            log.warn("Statement line {} could not be stored as JSON: {}", line.receipt(), ex.getMessage());
            return null;
        }
    }

    private MpesaShortCode shortcode(UUID uuid) {
        return shortcodes.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Shortcode not found"));
    }

    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
