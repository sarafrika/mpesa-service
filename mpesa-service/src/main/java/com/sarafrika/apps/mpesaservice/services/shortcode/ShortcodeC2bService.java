package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.C2BRegistrationResponse;
import com.sarafrika.apps.mpesaservice.dtos.C2BSimulationResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkPushResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bResponseType;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeCallbackUrlsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SimulateC2bRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeStatus;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SimulateC2bResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.TestStkRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.TestStkResultDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.models.C2bRegistration;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.C2bRegistrationRepository;
import com.sarafrika.apps.mpesaservice.repositories.ShortcodeAdminRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Registers C2B URLs with Safaricom (keeping every attempt), runs sandbox C2B simulations and test STK pushes. */
@Service
@Slf4j
public class ShortcodeC2bService {

    static final String PRODUCTION_REGISTERED_ONCE = "Safaricom registers production C2B URLs once. Delete the "
            + "current URLs on the M-PESA org portal (Self Services > URL Management, approved by two operators), "
            + "then register again.";

    private final ShortcodeAdminService shortcodes;
    private final C2bRegistrationRepository registrations;
    private final ShortcodeAdminRepository shortcodeRepository;
    private final MpesaDarajaService darajaService;
    private final AuditService auditService;
    private final CallbackUrls callbackUrls;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public ShortcodeC2bService(ShortcodeAdminService shortcodes, C2bRegistrationRepository registrations,
                               ShortcodeAdminRepository shortcodeRepository, MpesaDarajaService darajaService,
                               AuditService auditService, CallbackUrls callbackUrls, ObjectMapper objectMapper,
                               PlatformTransactionManager transactionManager) {
        this.shortcodes = shortcodes;
        this.registrations = registrations;
        this.shortcodeRepository = shortcodeRepository;
        this.darajaService = darajaService;
        this.auditService = auditService;
        this.callbackUrls = callbackUrls;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Page<C2bRegistrationDto> history(UUID shortcodeUuid, Pageable pageable) {
        return transaction.execute(status -> {
            shortcodes.find(shortcodeUuid);
            return registrations.findByShortcodeUuid(shortcodeUuid, pageable).map(C2bRegistrationDto::from);
        });
    }

    public C2bRegistrationDto register(UUID shortcodeUuid, C2bResponseType responseType) {
        return register(shortcodeUuid, responseType, false);
    }

    /** The outcome is saved either way; a refusal then answers 502, an unreachable Safaricom 503. Production
     * URLs register once, so a second registration needs replaceExisting after the old ones were deleted. */
    public C2bRegistrationDto register(UUID shortcodeUuid, C2bResponseType responseType, boolean replaceExisting) {
        MpesaShortCode code = transaction.execute(status -> shortcodes.find(shortcodeUuid));
        ShortcodeCapabilities.require(code, ShortcodeCapability.C2B_REGISTER);
        if (code.getEnvironment() == Environment.PRODUCTION && !replaceExisting
                && Boolean.TRUE.equals(transaction.execute(
                        status -> registrations.existsByShortcodeUuidAndSuccessfulTrue(shortcodeUuid)))) {
            throw new ConflictException(PRODUCTION_REGISTERED_ONCE);
        }
        ShortcodeCallbackUrlsDto urls = callbackUrls.forShortcode(code);
        MpesaApiResponse<C2BRegistrationResponse> response = darajaService.registerC2BUrls(shortcodeUuid,
                urls.c2bConfirmationUrl(), urls.c2bValidationUrl(), responseType.name());
        boolean ok = response != null && response.success() && response.data() != null;

        C2bRegistration saved = transaction.execute(status -> {
            C2bRegistration registration = new C2bRegistration();
            registration.setShortcodeUuid(shortcodeUuid);
            registration.setResponseType(responseType.name());
            registration.setConfirmationUrl(urls.c2bConfirmationUrl());
            registration.setValidationUrl(urls.c2bValidationUrl());
            registration.setSuccessful(ok);
            registration.setResultCode(ok ? "0" : response != null && response.error() != null
                    ? response.error().errorCode() : "NO_RESPONSE");
            registration.setResultDesc(ok ? response.data().responseDescription()
                    : response != null && response.error() != null ? response.error().errorMessage() : null);
            registration.setRaw(json(response != null ? (ok ? response.data() : response.error()) : null));
            registration.setRegisteredBy(CurrentActor.name());
            C2bRegistration stored = registrations.saveAndFlush(registration);
            if (ok) {
                MpesaShortCode fresh = shortcodes.find(shortcodeUuid);
                fresh.setConfirmationUrl(urls.c2bConfirmationUrl());
                fresh.setValidationUrl(urls.c2bValidationUrl());
                shortcodeRepository.save(fresh);
            }
            auditService.record(ok ? "SHORTCODE_C2B_REGISTERED" : "SHORTCODE_C2B_REGISTRATION_FAILED",
                    ShortcodeAdminService.ENTITY, shortcodeUuid.toString(), null, C2bRegistrationDto.from(stored));
            return stored;
        });
        if (!ok) {
            throw DarajaErrors.toUpstream(response != null ? response.error() : null, "register the C2B URLs");
        }
        return C2bRegistrationDto.from(saved);
    }

    /** Sandbox only: production C2B payments come from real customers. */
    public SimulateC2bResultDto simulate(UUID shortcodeUuid, SimulateC2bRequest request) {
        MpesaShortCode code = transaction.execute(status -> shortcodes.find(shortcodeUuid));
        if (code.getEnvironment() != Environment.SANDBOX) {
            throw new ConflictException("C2B payments can only be simulated on a sandbox shortcode");
        }
        ShortcodeCapabilities.require(code, ShortcodeCapability.C2B_REGISTER);
        MpesaApiResponse<C2BSimulationResponse> response = darajaService.simulateC2BPayment(shortcodeUuid,
                request.phoneNumber(), request.amount(), request.billRefNumber());
        boolean ok = response != null && response.success() && response.data() != null;
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("amount", request.amount());
        after.put("bill_ref_number", request.billRefNumber());
        after.put("accepted", ok);
        transaction.executeWithoutResult(status -> auditService.record("SHORTCODE_C2B_SIMULATED",
                ShortcodeAdminService.ENTITY, shortcodeUuid.toString(), null, after));
        if (!ok) {
            throw DarajaErrors.toUpstream(response != null ? response.error() : null, "simulate the C2B payment");
        }
        C2BSimulationResponse data = response.data();
        return new SimulateC2bResultDto(data.conversationId(), data.originatorConversationId(),
                data.responseDescription());
    }

    /** Calls Daraja directly, so the console needs no product grant; live shortcodes only, at most KES 10. */
    public TestStkResultDto testStk(UUID shortcodeUuid, TestStkRequest request) {
        MpesaShortCode code = transaction.execute(status -> shortcodes.find(shortcodeUuid));
        ShortcodeCapabilities.require(code, ShortcodeCapability.STK_PUSH);
        if (ShortcodeViews.status(code) != ShortcodeStatus.ACTIVE) {
            throw new ConflictException("Activate the shortcode before sending a test STK push");
        }
        BigDecimal amount = request.amountOrDefault();
        String account = code.getAccountReference() == null || code.getAccountReference().isBlank()
                ? "TEST" : code.getAccountReference();
        MpesaApiResponse<StkPushResponse> response = darajaService.initiateSTKPush(shortcodeUuid,
                request.phoneNumber(), amount, account, "Console test");
        boolean ok = response != null && response.success() && response.data() != null;
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("amount", amount);
        after.put("accepted", ok);
        after.put("checkout_request_id", ok ? response.data().checkoutRequestId() : null);
        transaction.executeWithoutResult(status -> auditService.record("SHORTCODE_TEST_STK_SENT",
                ShortcodeAdminService.ENTITY, shortcodeUuid.toString(), null, after));
        if (!ok) {
            throw DarajaErrors.toUpstream(response != null ? response.error() : null, "send the test STK push");
        }
        StkPushResponse data = response.data();
        return new TestStkResultDto(data.checkoutRequestId(), data.merchantRequestId(), data.customerMessage());
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Could not keep Safaricom's C2B registration reply: {}", ex.getMessage());
            return null;
        }
    }
}
