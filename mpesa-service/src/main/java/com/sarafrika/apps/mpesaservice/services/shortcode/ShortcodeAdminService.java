package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationState;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ConnectionTestResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.CreateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.UpdateShortcodeCredentialsRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.UpdateShortcodeOperationsRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeCallbackUrlsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeStatus;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeTotalsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.UpdateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaOutgoingPaymentRepository;
import com.sarafrika.apps.mpesaservice.repositories.ShortcodeAdminRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.payout.SecurityCredentialService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** The console's view of paybills and tills: list, inspect, onboard, edit, update credentials, check and switch on or off. */
@Service
public class ShortcodeAdminService {

    static final String ENTITY = "SHORTCODE";

    private final ShortcodeAdminRepository repository;
    private final ShortcodeHealthReader healthReader;
    private final ShortcodeConnectionTester connectionTester;
    private final MpesaDarajaService darajaService;
    private final AuditService auditService;
    private final CallbackUrls callbackUrls;
    private final ClientShortcodeGrantRepository grants;
    private final MpesaOutgoingPaymentRepository payouts;
    private final SecurityCredentialService certificates;
    private final TransactionTemplate transaction;
    // Off for now: the initiator is asked for but never blocks go-live or turning an operation on.
    private final boolean requireInitiator;

    public ShortcodeAdminService(ShortcodeAdminRepository repository, ShortcodeHealthReader healthReader,
                                 ShortcodeConnectionTester connectionTester, MpesaDarajaService darajaService,
                                 AuditService auditService, CallbackUrls callbackUrls,
                                 ClientShortcodeGrantRepository grants, MpesaOutgoingPaymentRepository payouts,
                                 SecurityCredentialService certificates,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${mpesa.shortcodes.require-initiator:false}") boolean requireInitiator) {
        this.repository = repository;
        this.healthReader = healthReader;
        this.connectionTester = connectionTester;
        this.darajaService = darajaService;
        this.auditService = auditService;
        this.callbackUrls = callbackUrls;
        this.grants = grants;
        this.payouts = payouts;
        this.certificates = certificates;
        this.transaction = new TransactionTemplate(transactionManager);
        this.requireInitiator = requireInitiator;
    }

    /** Optional filters; blank or null values are ignored. q matches business name or shortcode;
     * clientId keeps the shortcodes that product holds an active grant on. */
    public record ShortcodeQuery(ShortcodeType type, Environment environment, Boolean active, ShortcodeStatus status,
                                 String q, String clientId) {

        public ShortcodeQuery(ShortcodeType type, Environment environment, Boolean active, ShortcodeStatus status,
                              String q) {
            this(type, environment, active, status, q, null);
        }
    }

    @Transactional(readOnly = true)
    public Page<ShortcodeSummaryDto> list(ShortcodeQuery query, Pageable pageable) {
        Page<MpesaShortCode> page = repository.findAll(matching(query), pageable);
        Map<UUID, ShortcodeHealthDto> health = healthReader.healthOf(page.getContent());
        Map<UUID, List<ShortcodeSummaryDto.ProductRef>> products = productsOf(page.getContent());
        return page.map(code -> ShortcodeViews.summary(code, health.get(code.getUuid()),
                products.getOrDefault(code.getUuid(), List.of())));
    }

    private Map<UUID, List<ShortcodeSummaryDto.ProductRef>> productsOf(List<MpesaShortCode> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        List<UUID> uuids = codes.stream().map(MpesaShortCode::getUuid).toList();
        Map<UUID, List<ShortcodeSummaryDto.ProductRef>> result = new LinkedHashMap<>();
        grants.findByShortcodeUuidInAndActiveTrue(uuids).stream()
                .sorted(Comparator.comparing(ClientShortcodeGrant::getClientId))
                .forEach(g -> result.computeIfAbsent(g.getShortcodeUuid(), k -> new ArrayList<>())
                        .add(new ShortcodeSummaryDto.ProductRef(g.getClientId(), g.getDisplayName())));
        return result;
    }

    @Transactional(readOnly = true)
    public ShortcodeTotalsDto totals() {
        List<MpesaShortCode> all = repository.findAll();
        Map<UUID, ShortcodeHealthDto> health = healthReader.healthOf(all);
        long active = 0, suspended = 0, draft = 0, production = 0, c2bApplicable = 0, c2bRegistered = 0;
        long floatKnown = 0;
        BigDecimal totalFloat = BigDecimal.ZERO;
        for (MpesaShortCode code : all) {
            ShortcodeStatus status = ShortcodeViews.status(code);
            ShortcodeHealthDto h = health.get(code.getUuid());
            switch (status) {
                case ACTIVE -> active++;
                case SUSPENDED -> suspended++;
                case DRAFT -> draft++;
            }
            if (code.getEnvironment() == Environment.PRODUCTION) {
                production++;
            }
            // Drafts count too: their URLs must be registered before payments reach them.
            if (status != ShortcodeStatus.SUSPENDED && h.c2bState() != C2bRegistrationState.NOT_APPLICABLE) {
                c2bApplicable++;
                if (h.c2bState() == C2bRegistrationState.REGISTERED) {
                    c2bRegistered++;
                }
            }
            if (h.floatUtility() != null) {
                floatKnown++;
                totalFloat = totalFloat.add(h.floatUtility());
            }
        }
        return new ShortcodeTotalsDto(all.size(), active, suspended, draft, production, all.size() - production,
                c2bApplicable, c2bRegistered, totalFloat, floatKnown,
                payouts.countByResultCodeAndProcessedAtAfter(DarajaHints.INVALID_INITIATOR,
                        LocalDateTime.now().minusHours(24)));
    }

    @Transactional(readOnly = true)
    public ShortcodeDetailDto get(UUID uuid) {
        return detail(find(uuid));
    }

    public ShortcodeCallbackUrlsDto callbackUrls() {
        return callbackUrls.template();
    }

    /** Saves a draft: it takes no traffic until an admin activates it after a passing token check. */
    @Transactional
    public ShortcodeDetailDto create(CreateShortcodeRequest request) {
        if (request.environment() == Environment.PRODUCTION && !CurrentActor.isAdmin()) {
            throw new AccessDeniedException("Production shortcodes need mpesa-admin");
        }
        checkLimits(request.minAmount(), request.maxAmount());
        ShortcodeType type = request.shortcodeType();
        rejectPasskeyForB2c(type, request.passkey());
        Set<ShortcodeOperation> operations = request.operations() == null
                ? ShortcodeCapabilities.defaultOperations(type) : allowedOperations(type, request.operations());
        requireCertificateFor(request.environment(), request.initiatorPassword());
        String shortcode = request.shortcode().trim();
        String storeNumber = blankToNull(request.storeNumber());
        if (type == ShortcodeType.TILL && storeNumber == null) {
            throw new InvalidRequestException("store_number", "is required for a till: enter the store or Head "
                    + "Office number the till went live under");
        }
        if (type != ShortcodeType.TILL && storeNumber != null) {
            throw new InvalidRequestException("store_number", "is only for tills");
        }
        if (repository.exists((root, q, cb) -> cb.equal(root.get("shortcode"), shortcode))) {
            throw new ConflictException("Shortcode " + shortcode + " already exists");
        }
        if (storeNumber != null) {
            checkStoreNumberFree(storeNumber, null);
        }
        String actor = CurrentActor.name();
        LocalDateTime now = LocalDateTime.now();
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(shortcode);
        code.setShortcodeType(type);
        code.setOperations(operations);
        code.setStoreNumber(storeNumber);
        code.setStoreNumberConfirmedAt(storeNumber == null ? null : now);
        code.setBusinessName(request.businessName().trim());
        code.setEnvironment(request.environment());
        code.setConsumerKey(request.consumerKey().trim());
        code.setConsumerSecret(request.consumerSecret().trim());
        code.setPasskey(blankToNull(request.passkey()));
        code.setInitiatorName(blankToNull(request.initiatorName()));
        code.setInitiatorPassword(blankToNull(request.initiatorPassword()));
        code.setSecurityCredential(blankToNull(request.securityCredential()));
        if (code.getInitiatorPassword() != null || code.getSecurityCredential() != null) {
            code.setInitiatorCredentialSetAt(now);
        }
        if (request.minAmount() != null) {
            code.setMinAmount(request.minAmount());
        }
        if (request.maxAmount() != null) {
            code.setMaxAmount(request.maxAmount());
        }
        code.setAccountReference(blankToNull(request.accountReference()));
        if (StringUtils.hasText(request.transactionDesc())) {
            code.setTransactionDesc(request.transactionDesc().trim());
        }
        code.setCallbackUrl(callbackUrls.all().stkCallbackUrl());
        code.setIsActive(false);
        code.setCredentialsUpdatedAt(now);
        code.setCreatedBy(actor);
        code.setUpdatedBy(actor);
        try {
            code = repository.saveAndFlush(code);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Shortcode " + shortcode + " already exists");
        }
        auditService.record("SHORTCODE_CREATED", ENTITY, code.getUuid().toString(), null, ShortcodeViews.profile(code));
        return detail(code);
    }

    @Transactional
    public ShortcodeDetailDto update(UUID uuid, UpdateShortcodeRequest given) {
        MpesaShortCode code = find(uuid);
        UpdateShortcodeRequest request = withoutEchoedUrls(code, given);
        requireAdminToRepointProduction(code, request);
        Map<String, Object> before = ShortcodeViews.profile(code);
        BigDecimal min = request.minAmount() != null ? request.minAmount() : code.getMinAmount();
        BigDecimal max = request.maxAmount() != null ? request.maxAmount() : code.getMaxAmount();
        checkLimits(min, max);
        if (request.businessName() != null) {
            code.setBusinessName(request.businessName().trim());
        }
        code.setMinAmount(min);
        code.setMaxAmount(max);
        apply(request.accountReference(), code::setAccountReference);
        if (request.transactionDesc() != null) {
            code.setTransactionDesc(StringUtils.hasText(request.transactionDesc())
                    ? request.transactionDesc().trim() : "Payment");
        }
        if (request.callbackUrl() != null) {
            code.setCallbackUrl(StringUtils.hasText(request.callbackUrl())
                    ? request.callbackUrl().trim() : callbackUrls.all().stkCallbackUrl());
        }
        apply(request.b2cResultUrl(), code::setB2cResultUrl);
        apply(request.b2cTimeoutUrl(), code::setB2cTimeoutUrl);
        String storeNumber = blankToNull(request.storeNumber());
        boolean storeChanged = false;
        if (storeNumber != null) {
            if (code.getShortcodeType() != ShortcodeType.TILL) {
                throw new InvalidRequestException("store_number", "is only for tills");
            }
            checkStoreNumberFree(storeNumber, code.getId());
            storeChanged = !storeNumber.equals(code.getStoreNumber());
            code.setStoreNumber(storeNumber);
            code.setStoreNumberConfirmedAt(LocalDateTime.now());
        }
        code.setUpdatedBy(CurrentActor.name());
        code = repository.saveAndFlush(code);
        if (storeChanged) {
            // The passkey and token belong to the Go-Live number; let the next call fetch afresh.
            darajaService.evictAccessToken(uuid);
        }
        auditService.record("SHORTCODE_UPDATED", ENTITY, uuid.toString(), before, ShortcodeViews.profile(code));
        return detail(code);
    }

    /** Replaces the operations; on a live shortcode each one turned on must already have its credential. */
    @Transactional
    public ShortcodeDetailDto updateOperations(UUID uuid, UpdateShortcodeOperationsRequest request) {
        MpesaShortCode code = find(uuid);
        Set<ShortcodeOperation> wanted = allowedOperations(code.getShortcodeType(), request.operations());
        Set<ShortcodeOperation> current = ShortcodeCapabilities.operations(code);
        if (ShortcodeViews.status(code) == ShortcodeStatus.ACTIVE) {
            for (ShortcodeOperation operation : wanted) {
                String missing = current.contains(operation) ? null : missingCredential(code, operation, requireInitiator);
                if (missing != null) {
                    throw new ConflictException(missing);
                }
            }
        }
        code.setOperations(wanted);
        code.setUpdatedBy(CurrentActor.name());
        code = repository.saveAndFlush(code);
        auditService.record("SHORTCODE_OPERATIONS_UPDATED", ENTITY, uuid.toString(),
                Map.of("operations", List.copyOf(current)), Map.of("operations", List.copyOf(wanted)));
        return detail(code);
    }

    // A 400 on operations for anything the type does not allow.
    private static Set<ShortcodeOperation> allowedOperations(ShortcodeType type,
                                                             Collection<ShortcodeOperation> requested) {
        Set<ShortcodeOperation> available = ShortcodeCapabilities.availableOperations(type);
        EnumSet<ShortcodeOperation> wanted = EnumSet.noneOf(ShortcodeOperation.class);
        for (ShortcodeOperation operation : requested) {
            if (operation == null || !available.contains(operation)) {
                throw new InvalidRequestException("operations", "must be among " + available + " for a "
                        + ShortcodeCapabilities.label(type));
            }
            wanted.add(operation);
        }
        return wanted;
    }

    /** The sentence asking for an operation's missing credential, or null when it has what it needs. */
    static String missingCredential(MpesaShortCode code, ShortcodeOperation operation, boolean requireInitiator) {
        return switch (operation.needs()) {
            case NOTHING -> null;
            case PASSKEY -> StringUtils.hasText(code.getPasskey()) ? null
                    : "Add the passkey under Credentials first: " + operation.label() + " " + operation.needVerb()
                            + " it";
            case INITIATOR -> !requireInitiator || StringUtils.hasText(code.getInitiatorName())
                    && (StringUtils.hasText(code.getInitiatorPassword())
                    || StringUtils.hasText(code.getSecurityCredential())) ? null
                    : "Add the API initiator under Credentials first: " + operation.label() + " "
                            + operation.needVerb() + " it";
        };
    }

    // The password is encrypted with Safaricom's certificate for the environment, which this server must hold.
    private void requireCertificateFor(Environment environment, String initiatorPassword) {
        if (StringUtils.hasText(initiatorPassword) && !certificates.hasCertificate(environment)) {
            throw new InvalidRequestException("initiator_password", "This server has no Safaricom "
                    + environment.name().toLowerCase(Locale.ROOT) + " certificate, so paste the security credential "
                    + "from the Daraja portal instead");
        }
    }

    private static void rejectPasskeyForB2c(ShortcodeType type, String passkey) {
        // "Passkey is only required for M-PESA Express/STK Push" (B2C FAQ 15, SHORTCODE-TYPES.md).
        if (type == ShortcodeType.B2C && StringUtils.hasText(passkey)) {
            throw new InvalidRequestException("passkey", "is not used by a B2C shortcode: it is only for STK push");
        }
    }

    // A store number must not be another shortcode's number, or another till's store number.
    private void checkStoreNumberFree(String storeNumber, Long ownId) {
        boolean taken = repository.exists((root, q, cb) -> cb.and(
                ownId == null ? cb.conjunction() : cb.notEqual(root.get("id"), ownId),
                cb.or(cb.equal(root.get("shortcode"), storeNumber), cb.equal(root.get("storeNumber"), storeNumber))));
        if (taken) {
            throw new ConflictException("Store number " + storeNumber + " is already used by another shortcode");
        }
    }

    /** Stores credentials already changed at Safaricom (Daraja has no API to change them), then re-runs
     * the token check so the console shows at once whether the new values work. */
    public ShortcodeDetailDto updateCredentials(UUID uuid, UpdateShortcodeCredentialsRequest request) {
        List<String> changed = new ArrayList<>();
        transaction.executeWithoutResult(status -> {
            MpesaShortCode code = find(uuid);
            rejectPasskeyForB2c(code.getShortcodeType(), request.passkey());
            requireCertificateFor(code.getEnvironment(), request.initiatorPassword());
            Map<String, Object> before = Map.of("credentials", ShortcodeViews.credentials(code));
            replace(request.consumerKey(), "consumer_key", code::setConsumerKey, changed);
            replace(request.consumerSecret(), "consumer_secret", code::setConsumerSecret, changed);
            replace(request.passkey(), "passkey", code::setPasskey, changed);
            replace(request.initiatorName(), "initiator_name", code::setInitiatorName, changed);
            replace(request.initiatorPassword(), "initiator_password", code::setInitiatorPassword, changed);
            replace(request.securityCredential(), "security_credential", code::setSecurityCredential, changed);
            if (changed.isEmpty()) {
                throw new InvalidRequestException("credentials", "Enter at least one new credential");
            }
            LocalDateTime now = LocalDateTime.now();
            code.setCredentialsUpdatedAt(now);
            if (changed.contains("initiator_password") || changed.contains("security_credential")) {
                code.setInitiatorCredentialSetAt(now);
            }
            code.setLastConnectionOk(null);
            code.setLastConnectionError(null);
            code.setUpdatedBy(CurrentActor.name());
            code = repository.saveAndFlush(code);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("changed", changed);
            after.put("credentials", request);
            auditService.record("SHORTCODE_CREDENTIALS_UPDATED", ENTITY, uuid.toString(), before, after);
        });
        darajaService.evictAccessToken(uuid);
        testConnection(uuid);
        return transaction.execute(status -> detail(find(uuid)));
    }

    /** Asks Daraja for a token outside any transaction, then records the outcome. */
    public ConnectionTestResultDto testConnection(UUID uuid) {
        MpesaShortCode code = transaction.execute(status -> find(uuid));
        ConnectionTestResultDto result = connectionTester.test(code);
        transaction.executeWithoutResult(status -> {
            MpesaShortCode fresh = find(uuid);
            fresh.setLastConnectionOk(result.ok());
            fresh.setLastConnectionCheckedAt(result.checkedAt());
            fresh.setLastConnectionLatencyMs((int) Math.min(Integer.MAX_VALUE, result.latencyMs()));
            fresh.setLastConnectionError(truncate(result.error(), 500));
            repository.save(fresh);
            auditService.record("SHORTCODE_CONNECTION_TESTED", ENTITY, uuid.toString(), null, result);
        });
        return result;
    }

    @Transactional
    public ShortcodeDetailDto activate(UUID uuid) {
        MpesaShortCode code = find(uuid);
        ShortcodeStatus before = ShortcodeViews.status(code);
        if (before == ShortcodeStatus.ACTIVE) {
            throw new ConflictException("Shortcode " + code.getShortcode() + " is already active");
        }
        if (before == ShortcodeStatus.DRAFT && !Boolean.TRUE.equals(code.getLastConnectionOk())) {
            throw new ConflictException("Run the connection check first: Daraja must issue a token for these "
                    + "credentials before the shortcode goes live");
        }
        requireReadyToGoLive(code);
        LocalDateTime now = LocalDateTime.now();
        code.setIsActive(true);
        if (code.getActivatedAt() == null) {
            code.setActivatedAt(now);
        }
        code.setSuspendedAt(null);
        code.setUpdatedBy(CurrentActor.name());
        code = repository.saveAndFlush(code);
        auditService.record("SHORTCODE_ACTIVATED", ENTITY, uuid.toString(), Map.of("status", before),
                Map.of("status", ShortcodeStatus.ACTIVE));
        return detail(code);
    }

    @Transactional
    public ShortcodeDetailDto suspend(UUID uuid, String reason) {
        MpesaShortCode code = find(uuid);
        ShortcodeStatus before = ShortcodeViews.status(code);
        if (before != ShortcodeStatus.ACTIVE) {
            throw new ConflictException("Shortcode " + code.getShortcode() + " is not active");
        }
        code.setIsActive(false);
        code.setSuspendedAt(LocalDateTime.now());
        code.setUpdatedBy(CurrentActor.name());
        code = repository.saveAndFlush(code);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", ShortcodeStatus.SUSPENDED);
        after.put("reason", StringUtils.hasText(reason) ? reason.trim() : null);
        auditService.record("SHORTCODE_SUSPENDED", ENTITY, uuid.toString(), Map.of("status", before), after);
        return detail(code);
    }

    // Credentials follow the operations turned on: the passkey for STK push, the initiator for the rest.
    private void requireReadyToGoLive(MpesaShortCode code) {
        for (ShortcodeOperation operation : ShortcodeCapabilities.operations(code)) {
            String missing = missingCredential(code, operation, requireInitiator);
            if (missing != null) {
                throw new ConflictException(missing);
            }
        }
        if (code.getShortcodeType() == ShortcodeType.TILL && !StringUtils.hasText(code.getStoreNumber())) {
            throw new ConflictException("Add the till's store or Head Office number first");
        }
    }

    MpesaShortCode find(UUID uuid) {
        return repository.findOne((root, q, cb) -> cb.equal(root.get("uuid"), uuid))
                .orElseThrow(() -> new NotFoundException("Shortcode " + uuid + " not found"));
    }

    private ShortcodeDetailDto detail(MpesaShortCode code) {
        ShortcodeHealthDto health = healthReader.healthOf(List.of(code)).get(code.getUuid());
        return ShortcodeViews.detail(code, health, callbackUrls.stk(code),
                productsOf(List.of(code)).getOrDefault(code.getUuid(), List.of()));
    }

    // A URL sent back exactly as the detail showed it (token masked) is no change.
    private UpdateShortcodeRequest withoutEchoedUrls(MpesaShortCode code, UpdateShortcodeRequest r) {
        return new UpdateShortcodeRequest(r.businessName(), r.minAmount(), r.maxAmount(), r.accountReference(),
                r.transactionDesc(), echoed(r.callbackUrl(), callbackUrls.stk(code)),
                echoed(r.b2cResultUrl(), code.getB2cResultUrl()), echoed(r.b2cTimeoutUrl(), code.getB2cTimeoutUrl()),
                r.storeNumber());
    }

    private static String echoed(String requested, String current) {
        return requested != null && current != null && requested.trim().equals(CallbackUrls.masked(current))
                ? null : requested;
    }

    // Callback URLs decide where Safaricom posts production money events, so only an admin may repoint them.
    private static void requireAdminToRepointProduction(MpesaShortCode code, UpdateShortcodeRequest request) {
        if (code.getEnvironment() != Environment.PRODUCTION || CurrentActor.isAdmin()) {
            return;
        }
        if (changes(request.callbackUrl(), code.getCallbackUrl())
                || changes(request.b2cResultUrl(), code.getB2cResultUrl())
                || changes(request.b2cTimeoutUrl(), code.getB2cTimeoutUrl())) {
            throw new AccessDeniedException("Changing a production shortcode's callback URLs needs mpesa-admin");
        }
    }

    private static boolean changes(String requested, String current) {
        return requested != null && !Objects.equals(blankToNull(requested), current);
    }

    static Specification<MpesaShortCode> matching(ShortcodeQuery query) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.type() != null) {
                predicates.add(cb.equal(root.get("shortcodeType"), query.type()));
            }
            if (query.environment() != null) {
                predicates.add(cb.equal(root.get("environment"), query.environment()));
            }
            if (query.active() != null) {
                predicates.add(cb.equal(root.<Boolean>get("isActive"), query.active()));
            }
            if (query.status() != null) {
                predicates.add(switch (query.status()) {
                    case ACTIVE -> cb.isTrue(root.<Boolean>get("isActive"));
                    case SUSPENDED -> cb.and(cb.isFalse(root.<Boolean>get("isActive")), cb.isNotNull(root.get("activatedAt")));
                    case DRAFT -> cb.and(cb.isFalse(root.<Boolean>get("isActive")), cb.isNull(root.get("activatedAt")));
                });
            }
            if (StringUtils.hasText(query.clientId())) {
                Subquery<UUID> granted = criteria.subquery(UUID.class);
                Root<ClientShortcodeGrant> grant = granted.from(ClientShortcodeGrant.class);
                granted.select(grant.get("shortcodeUuid")).where(
                        cb.equal(grant.get("clientId"), query.clientId().trim()), cb.isTrue(grant.get("active")));
                predicates.add(root.get("uuid").in(granted));
            }
            if (StringUtils.hasText(query.q())) {
                String text = query.q().trim().toLowerCase(Locale.ROOT);
                String like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.<String>get("businessName")), like, '\\'),
                        cb.like(root.<String>get("shortcode"), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void checkLimits(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && max.compareTo(min) <= 0) {
            throw new InvalidRequestException("max_amount", "must be more than the minimum amount");
        }
    }

    private static void apply(String value, Consumer<String> setter) {
        if (value != null) {
            setter.accept(blankToNull(value));
        }
    }

    private static void replace(String value, String name, Consumer<String> setter, List<String> changed) {
        if (StringUtils.hasText(value)) {
            setter.accept(value.trim());
            changed.add(name);
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
