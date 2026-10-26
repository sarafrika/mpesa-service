package com.sarafrika.apps.mpesaservice.services.treasury;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.AccountBalanceResponse;
import com.sarafrika.apps.mpesaservice.dtos.AccountBalanceResultData;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.dtos.PayoutFloatDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.BalanceAccountDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.BalanceSnapshotDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.ShortcodeFloatStatusDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.BalanceSnapshot;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.BalanceSnapshotRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.metrics.AlertProperties;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.shortcode.DarajaErrors;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceQuerySource;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceSnapshotStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Account Balance queries: sent here (by a person or the schedule), answered later on the result URL, kept as
 * snapshots. Also the per-shortcode low-float threshold, which falls back to mpesa.alerts.low-float. */
@Slf4j
@Service
public class BalanceService {

    static final String AUDIT_QUERIED = "BALANCE_QUERIED";
    static final String AUDIT_THRESHOLD = "FLOAT_THRESHOLD_UPDATED";
    static final Duration MAX_HISTORY = Duration.ofDays(31);
    static final int MAX_HISTORY_POINTS = 1000;
    private static final TypeReference<List<BalanceAccountDto>> ACCOUNTS = new TypeReference<>() {
    };

    private final BalanceSnapshotRepository snapshots;
    private final MpesaShortCodeRepository shortcodes;
    private final MpesaDarajaService daraja;
    private final MpesaPayoutService payouts;
    private final AuditService audit;
    private final AlertProperties alerts;
    private final TreasuryProperties treasury;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate newTransaction;

    public BalanceService(BalanceSnapshotRepository snapshots, MpesaShortCodeRepository shortcodes,
                          MpesaDarajaService daraja, MpesaPayoutService payouts, AuditService audit,
                          AlertProperties alerts, TreasuryProperties treasury, ObjectMapper objectMapper,
                          PlatformTransactionManager transactions) {
        this.snapshots = snapshots;
        this.shortcodes = shortcodes;
        this.daraja = daraja;
        this.payouts = payouts;
        this.audit = audit;
        this.alerts = alerts;
        this.treasury = treasury;
        this.objectMapper = objectMapper;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** From the console: sends the query and answers with the PENDING snapshot the result will fill. */
    public BalanceSnapshotDto requestQuery(UUID shortcodeUuid) {
        MpesaShortCode shortcode = shortcode(shortcodeUuid);
        if (!StringUtils.hasText(shortcode.getInitiatorName())) {
            throw new ConflictException("This shortcode has no initiator; Account Balance needs one. "
                    + "Add the initiator under Credentials first.");
        }
        Sent sent = send(shortcode, BalanceQuerySource.MANUAL);
        BalanceSnapshot snapshot = sent.snapshot();
        if (snapshot.getStatus() == BalanceSnapshotStatus.FAILED) {
            throw DarajaErrors.toUpstream(sent.error(), "query the balance");
        }
        audit.record(AUDIT_QUERIED, "SHORTCODE", shortcodeUuid.toString(), null,
                Map.of("snapshot_uuid", snapshot.getUuid().toString()));
        return toDto(snapshot);
    }

    /** The saved query and, when Safaricom refused it, Daraja's error. */
    public record Sent(BalanceSnapshot snapshot, MpesaErrorResponse error) {
    }

    /** Saves a PENDING snapshot (committed, so the result can find it), then asks Safaricom. A refusal marks
     * it FAILED; no answer at all leaves it PENDING for the result or the queue timeout to settle. */
    public Sent send(MpesaShortCode shortcode, BalanceQuerySource source) {
        BalanceSnapshot pending = new BalanceSnapshot();
        pending.setShortcodeUuid(shortcode.getUuid());
        pending.setSource(source);
        pending.setRequestedAt(LocalDateTime.now());
        String actor = CurrentActor.name();
        pending.setCreatedBy(actor);
        BalanceSnapshot saved = newTransaction.execute(status -> snapshots.saveAndFlush(pending));

        MpesaApiResponse<AccountBalanceResponse> response = daraja.queryAccountBalance(shortcode, "Account balance");
        BalanceSnapshot settled = newTransaction.execute(status -> {
            BalanceSnapshot row = snapshots.findById(saved.getId()).orElseThrow();
            if (response.success() && response.data() != null && response.data().isAccepted()) {
                row.setConversationId(response.data().conversationId());
                row.setOriginatorConversationId(response.data().originatorConversationId());
            } else {
                row.setStatus(BalanceSnapshotStatus.FAILED);
                row.setResultDesc(truncate(response.error() != null ? response.error().errorMessage()
                        : response.data() != null ? response.data().responseDescription()
                        : "Safaricom refused the balance query"));
                row.setReceivedAt(LocalDateTime.now());
            }
            return snapshots.save(row);
        });
        return new Sent(settled, response.success() ? null : response.error());
    }

    /** Safaricom's Account Balance result. Only a PENDING or TIMED_OUT query takes it, so repeats are ignored. */
    @Transactional
    public Optional<BalanceSnapshot> applyResult(AccountBalanceResultData result, String raw) {
        Optional<BalanceSnapshot> match = lock(result.originatorConversationId(), result.conversationId());
        if (match.isEmpty()) {
            log.warn("Account balance result for unknown query {} / {}", result.originatorConversationId(),
                    result.conversationId());
            return Optional.empty();
        }
        BalanceSnapshot snapshot = match.get();
        if (snapshot.getStatus() != BalanceSnapshotStatus.PENDING
                && snapshot.getStatus() != BalanceSnapshotStatus.TIMED_OUT) {
            log.info("Ignoring repeat balance result for snapshot {}", snapshot.getUuid());
            return match;
        }
        snapshot.setResultCode(result.resultCode());
        snapshot.setResultDesc(truncate(result.resultDesc()));
        snapshot.setRaw(raw);
        snapshot.setReceivedAt(LocalDateTime.now());
        String balance = result.resultParameters() == null || result.resultParameters().resultParameters() == null
                ? null : result.resultParameters().getAccountBalance();
        if (result.isSuccessful() && balance != null) {
            List<AccountBalanceParser.Account> accounts = AccountBalanceParser.parse(balance);
            snapshot.setStatus(BalanceSnapshotStatus.COMPLETED);
            snapshot.setAccounts(json(accounts.stream().map(BalanceService::dto).toList()));
            snapshot.setUtilityAvailable(available(accounts, AccountBalanceParser.UTILITY));
            snapshot.setWorkingAvailable(available(accounts, AccountBalanceParser.WORKING));
            snapshot.setChargesPaidAvailable(available(accounts, AccountBalanceParser.CHARGES_PAID));
        } else {
            snapshot.setStatus(BalanceSnapshotStatus.FAILED);
        }
        return Optional.of(snapshots.save(snapshot));
    }

    /** Safaricom could not process the query in time; a late result can still complete it. */
    @Transactional
    public Optional<BalanceSnapshot> applyTimeout(String originatorConversationId, String conversationId,
                                                  String description) {
        return lock(originatorConversationId, conversationId).map(snapshot -> {
            if (snapshot.getStatus() == BalanceSnapshotStatus.PENDING) {
                snapshot.setStatus(BalanceSnapshotStatus.TIMED_OUT);
                snapshot.setResultDesc(truncate(description));
                snapshot.setReceivedAt(LocalDateTime.now());
                return snapshots.save(snapshot);
            }
            return snapshot;
        });
    }

    @Transactional(readOnly = true)
    public ShortcodeFloatStatusDto status(UUID shortcodeUuid) {
        MpesaShortCode shortcode = shortcode(shortcodeUuid);
        Optional<BalanceSnapshot> latest = snapshots.findFirstByShortcodeUuidAndStatusOrderByReceivedAtDescIdDesc(
                shortcodeUuid, BalanceSnapshotStatus.COMPLETED);
        Optional<BalanceSnapshot> lastQuery = snapshots.findFirstByShortcodeUuidOrderByRequestedAtDescIdDesc(
                shortcodeUuid);
        KnownFloat known = knownFloat(shortcodeUuid, latest);
        BigDecimal threshold = thresholdOf(shortcode);
        return new ShortcodeFloatStatusDto(shortcodeUuid, latest.map(this::toDto).orElse(null),
                lastQuery.map(this::toDto).orElse(null), known.utility(), Times.offset(known.reportedAt()),
                known.source(), threshold, shortcode.getLowFloatThreshold(), alerts.lowFloat(),
                known.known() && known.utility().compareTo(threshold) < 0,
                treasury.scheduledQueriesOn() ? treasury.balanceQueryInterval().toMinutes() : null,
                StringUtils.hasText(shortcode.getInitiatorName()));
    }

    /** The newest utility balance known for a shortcode: a balance result or a settled payout's report. */
    @Transactional(readOnly = true)
    public KnownFloat knownFloat(UUID shortcodeUuid) {
        return knownFloat(shortcodeUuid, snapshots.findFirstByShortcodeUuidAndStatusOrderByReceivedAtDescIdDesc(
                shortcodeUuid, BalanceSnapshotStatus.COMPLETED));
    }

    private KnownFloat knownFloat(UUID shortcodeUuid, Optional<BalanceSnapshot> latest) {
        PayoutFloatDto payout = payouts.latestFloat(shortcodeUuid);
        return KnownFloat.newest(latest.map(BalanceSnapshot::getUtilityAvailable).orElse(null),
                latest.map(BalanceSnapshot::getReceivedAt).orElse(null),
                payout.utilityAccountAvailableFunds(), payout.reportedAt());
    }

    /** Completed snapshots received in [from, to), oldest first; defaults to the last 7 days, at most 31. */
    @Transactional(readOnly = true)
    public List<BalanceSnapshotDto> history(UUID shortcodeUuid, OffsetDateTime from, OffsetDateTime to) {
        shortcode(shortcodeUuid);
        LocalDateTime end = to == null ? LocalDateTime.now().plusSeconds(1) : Times.local(to);
        LocalDateTime start = from == null ? end.minusDays(7) : Times.local(from);
        if (!start.isBefore(end)) {
            throw new InvalidRequestException("from", "must be before to");
        }
        if (Duration.between(start, end).compareTo(MAX_HISTORY) > 0) {
            throw new InvalidRequestException("from", "the range can span at most 31 days");
        }
        return snapshots.history(shortcodeUuid, BalanceSnapshotStatus.COMPLETED, start, end,
                PageRequest.of(0, MAX_HISTORY_POINTS)).stream().map(this::toDto).toList();
    }

    @Transactional
    public ShortcodeFloatStatusDto updateThreshold(UUID shortcodeUuid, BigDecimal threshold) {
        MpesaShortCode shortcode = shortcode(shortcodeUuid);
        Map<String, Object> before = new HashMap<>();
        before.put("low_float_threshold", shortcode.getLowFloatThreshold());
        shortcode.setLowFloatThreshold(threshold);
        shortcode.setUpdatedBy(CurrentActor.name());
        shortcodes.save(shortcode);
        Map<String, Object> after = new HashMap<>();
        after.put("low_float_threshold", threshold);
        audit.record(AUDIT_THRESHOLD, "SHORTCODE", shortcodeUuid.toString(), before, after);
        return status(shortcodeUuid);
    }

    /** The shortcode's own threshold, else mpesa.alerts.low-float. */
    public BigDecimal thresholdOf(MpesaShortCode shortcode) {
        return shortcode.getLowFloatThreshold() != null ? shortcode.getLowFloatThreshold() : alerts.lowFloat();
    }

    public BalanceSnapshotDto toDto(BalanceSnapshot s) {
        return new BalanceSnapshotDto(s.getUuid(), s.getShortcodeUuid(), s.getStatus(), s.getSource(),
                accounts(s.getAccounts()), s.getUtilityAvailable(), s.getWorkingAvailable(),
                s.getChargesPaidAvailable(), s.getResultCode(), s.getResultDesc(), s.getCreatedBy(),
                Times.offset(s.getRequestedAt()), Times.offset(s.getReceivedAt()));
    }

    private Optional<BalanceSnapshot> lock(String originatorConversationId, String conversationId) {
        if (StringUtils.hasText(originatorConversationId)) {
            List<BalanceSnapshot> found = snapshots.lockByOriginatorConversationId(originatorConversationId);
            if (!found.isEmpty()) {
                return Optional.of(found.getFirst());
            }
        }
        if (StringUtils.hasText(conversationId)) {
            List<BalanceSnapshot> found = snapshots.lockByConversationId(conversationId);
            if (!found.isEmpty()) {
                return Optional.of(found.getFirst());
            }
        }
        return Optional.empty();
    }

    private MpesaShortCode shortcode(UUID uuid) {
        return shortcodes.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Shortcode not found"));
    }

    private static BalanceAccountDto dto(AccountBalanceParser.Account a) {
        return new BalanceAccountDto(a.name(), a.currency(), a.available(), a.current(), a.reserved(),
                a.uncleared());
    }

    private static BigDecimal available(List<AccountBalanceParser.Account> accounts, String name) {
        return AccountBalanceParser.find(accounts, name).map(AccountBalanceParser.Account::available).orElse(null);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not write the balance accounts", ex);
        }
    }

    private List<BalanceAccountDto> accounts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, ACCOUNTS);
        } catch (JsonProcessingException ex) {
            log.warn("Unreadable balance accounts: {}", ex.getMessage());
            return List.of();
        }
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
