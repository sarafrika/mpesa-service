package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationState;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ConnectionTestResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.CreateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.RotateShortcodeCredentialsRequest;
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
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.ShortcodeAdminRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** The console's view of paybills and tills: list, inspect, onboard, edit, rotate, check and switch on or off. */
@Service
public class ShortcodeAdminService {

    static final String ENTITY = "SHORTCODE";

    private final ShortcodeAdminRepository repository;
    private final ShortcodeHealthReader healthReader;
    private final ShortcodeConnectionTester connectionTester;
    private final MpesaDarajaService darajaService;
    private final AuditService auditService;
    private final CallbackUrls callbackUrls;
    private final TransactionTemplate transaction;
    private final int rotationDays;

    public ShortcodeAdminService(ShortcodeAdminRepository repository, ShortcodeHealthReader healthReader,
                                 ShortcodeConnectionTester connectionTester, MpesaDarajaService darajaService,
                                 AuditService auditService, CallbackUrls callbackUrls,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${mpesa.credentials.rotation-days:90}") int rotationDays) {
        this.repository = repository;
        this.healthReader = healthReader;
        this.connectionTester = connectionTester;
        this.darajaService = darajaService;
        this.auditService = auditService;
        this.callbackUrls = callbackUrls;
        this.transaction = new TransactionTemplate(transactionManager);
        this.rotationDays = rotationDays;
    }

    /** Optional filters; blank or null values are ignored. q matches business name or shortcode. */
    public record ShortcodeQuery(ShortcodeType type, Environment environment, Boolean active, ShortcodeStatus status,
                                 String q) {
    }

    @Transactional(readOnly = true)
    public Page<ShortcodeSummaryDto> list(ShortcodeQuery query, Pageable pageable) {
        Page<MpesaShortCode> page = repository.findAll(matching(query), pageable);
        Map<UUID, ShortcodeHealthDto> health = healthReader.healthOf(page.getContent());
        LocalDateTime now = LocalDateTime.now();
        return page.map(code -> ShortcodeViews.summary(code, health.get(code.getUuid()), rotationDays, now));
    }

    @Transactional(readOnly = true)
    public ShortcodeTotalsDto totals() {
        List<MpesaShortCode> all = repository.findAll();
        Map<UUID, ShortcodeHealthDto> health = healthReader.healthOf(all);
        LocalDateTime now = LocalDateTime.now();
        long active = 0, suspended = 0, draft = 0, production = 0, c2bApplicable = 0, c2bRegistered = 0;
        long floatKnown = 0, due = 0;
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
            if (status == ShortcodeStatus.ACTIVE && h.c2bState() != C2bRegistrationState.NOT_APPLICABLE) {
                c2bApplicable++;
                if (h.c2bState() == C2bRegistrationState.REGISTERED) {
                    c2bRegistered++;
                }
            }
            if (h.floatUtility() != null) {
                floatKnown++;
                totalFloat = totalFloat.add(h.floatUtility());
            }
            if (ShortcodeViews.rotationDue(code, rotationDays, now)) {
                due++;
            }
        }
        return new ShortcodeTotalsDto(all.size(), active, suspended, draft, production, all.size() - production,
                c2bApplicable, c2bRegistered, totalFloat, floatKnown, due, rotationDays);
    }

    @Transactional(readOnly = true)
    public ShortcodeDetailDto get(UUID uuid) {
        return detail(find(uuid));
    }

    public ShortcodeCallbackUrlsDto callbackUrls() {
        return callbackUrls.all();
    }

    /** Saves a draft: it takes no traffic until an admin activates it after a passing token check. */
    @Transactional
    public ShortcodeDetailDto create(CreateShortcodeRequest request) {
        if (request.environment() == Environment.PRODUCTION && !CurrentActor.isAdmin()) {
            throw new AccessDeniedException("Production shortcodes need mpesa-admin");
        }
        checkLimits(request.minAmount(), request.maxAmount());
        if (request.shortcodeType() == ShortcodeType.B2C) {
            if (!StringUtils.hasText(request.initiatorName())) {
                throw new InvalidRequestException("initiator_name", "is required for a B2C shortcode");
            }
            if (!StringUtils.hasText(request.initiatorPassword()) && !StringUtils.hasText(request.securityCredential())) {
                throw new InvalidRequestException("initiator_password",
                        "or a security credential is required for a B2C shortcode");
            }
        }
        String shortcode = request.shortcode().trim();
        if (repository.exists((root, q, cb) -> cb.equal(root.get("shortcode"), shortcode))) {
            throw new ConflictException("Shortcode " + shortcode + " already exists");
        }
        String actor = CurrentActor.name();
        LocalDateTime now = LocalDateTime.now();
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(shortcode);
        code.setShortcodeType(request.shortcodeType());
        code.setBusinessName(request.businessName().trim());
        code.setEnvironment(request.environment());
        code.setConsumerKey(request.consumerKey().trim());
        code.setConsumerSecret(request.consumerSecret().trim());
        code.setPasskey(blankToNull(request.passkey()));
        code.setInitiatorName(blankToNull(request.initiatorName()));
        code.setInitiatorPassword(blankToNull(request.initiatorPassword()));
        code.setSecurityCredential(blankToNull(request.securityCredential()));
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
        code.setCredentialsRotatedAt(now);
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
    public ShortcodeDetailDto update(UUID uuid, UpdateShortcodeRequest request) {
        MpesaShortCode code = find(uuid);
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
        code.setUpdatedBy(CurrentActor.name());
        code = repository.saveAndFlush(code);
        auditService.record("SHORTCODE_UPDATED", ENTITY, uuid.toString(), before, ShortcodeViews.profile(code));
        return detail(code);
    }

    /** Replaces the given credentials; the token check must pass again before the shortcode is trusted. */
    public ShortcodeDetailDto rotateCredentials(UUID uuid, RotateShortcodeCredentialsRequest request) {
        List<String> changed = new ArrayList<>();
        ShortcodeDetailDto result = transaction.execute(status -> {
            MpesaShortCode code = find(uuid);
            Map<String, Object> before = Map.of("credentials",
                    ShortcodeViews.credentials(code, rotationDays, LocalDateTime.now()));
            rotate(request.consumerKey(), "consumer_key", code::setConsumerKey, changed);
            rotate(request.consumerSecret(), "consumer_secret", code::setConsumerSecret, changed);
            rotate(request.passkey(), "passkey", code::setPasskey, changed);
            rotate(request.initiatorName(), "initiator_name", code::setInitiatorName, changed);
            rotate(request.initiatorPassword(), "initiator_password", code::setInitiatorPassword, changed);
            rotate(request.securityCredential(), "security_credential", code::setSecurityCredential, changed);
            if (changed.isEmpty()) {
                throw new InvalidRequestException("credentials", "Enter at least one new credential");
            }
            code.setCredentialsRotatedAt(LocalDateTime.now());
            code.setLastConnectionOk(null);
            code.setLastConnectionError(null);
            code.setUpdatedBy(CurrentActor.name());
            code = repository.saveAndFlush(code);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("changed", changed);
            after.put("credentials", request);
            auditService.record("SHORTCODE_CREDENTIALS_ROTATED", ENTITY, uuid.toString(), before, after);
            return detail(code);
        });
        darajaService.evictAccessToken(uuid);
        return result;
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

    MpesaShortCode find(UUID uuid) {
        return repository.findOne((root, q, cb) -> cb.equal(root.get("uuid"), uuid))
                .orElseThrow(() -> new NotFoundException("Shortcode " + uuid + " not found"));
    }

    private ShortcodeDetailDto detail(MpesaShortCode code) {
        ShortcodeHealthDto health = healthReader.healthOf(List.of(code)).get(code.getUuid());
        return ShortcodeViews.detail(code, health, rotationDays, LocalDateTime.now());
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

    private static void rotate(String value, String name, Consumer<String> setter, List<String> changed) {
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
