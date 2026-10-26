package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.C2BRegistrationResponse;
import com.sarafrika.apps.mpesaservice.dtos.C2BSimulationResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bResponseType;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeCallbackUrlsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SimulateC2bRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SimulateC2bResultDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.models.C2bRegistration;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.C2bRegistrationRepository;
import com.sarafrika.apps.mpesaservice.repositories.ShortcodeAdminRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Registers C2B URLs with Safaricom (keeping every attempt) and runs sandbox C2B simulations. */
@Service
@Slf4j
public class ShortcodeC2bService {

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

    /** The outcome is saved either way; a refusal then answers 502, an unreachable Safaricom 503. */
    public C2bRegistrationDto register(UUID shortcodeUuid, C2bResponseType responseType) {
        MpesaShortCode code = transaction.execute(status -> shortcodes.find(shortcodeUuid));
        if (code.getShortcodeType() == ShortcodeType.B2C) {
            throw new ConflictException("A B2C shortcode takes no C2B payments, so it has no C2B URLs");
        }
        ShortcodeCallbackUrlsDto urls = callbackUrls.all();
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
        if (code.getShortcodeType() == ShortcodeType.B2C) {
            throw new ConflictException("A B2C shortcode takes no C2B payments");
        }
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
