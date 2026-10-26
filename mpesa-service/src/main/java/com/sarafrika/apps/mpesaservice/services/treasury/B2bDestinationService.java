package com.sarafrika.apps.mpesaservice.services.treasury;

import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bDestinationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.CreateB2bDestinationRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.UpdateB2bDestinationRequest;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.B2bDestination;
import com.sarafrika.apps.mpesaservice.repositories.B2bDestinationRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bReceiverType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** The paybills and tills a shortcode may sweep to; every change is audited. */
@Service
@RequiredArgsConstructor
public class B2bDestinationService {

    static final String AUDIT_CREATED = "B2B_DESTINATION_CREATED";
    static final String AUDIT_UPDATED = "B2B_DESTINATION_UPDATED";
    static final String AUDIT_DELETED = "B2B_DESTINATION_DELETED";
    static final String ENTITY = "B2B_DESTINATION";

    private final B2bDestinationRepository destinations;
    private final MpesaShortCodeRepository shortcodes;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Page<B2bDestinationDto> list(UUID shortcodeUuid, Boolean active, Pageable pageable) {
        requireShortcode(shortcodeUuid);
        return destinations.findForShortcode(shortcodeUuid, active, pageable).map(B2bDestinationDto::from);
    }

    @Transactional
    public B2bDestinationDto create(UUID shortcodeUuid, CreateB2bDestinationRequest request) {
        requireShortcode(shortcodeUuid);
        String name = request.name().trim();
        if (destinations.nameTaken(shortcodeUuid, name, null)) {
            throw new ConflictException("This shortcode already has a destination called " + name);
        }
        B2bDestination destination = new B2bDestination();
        destination.setShortcodeUuid(shortcodeUuid);
        destination.setName(name);
        destination.setReceiverShortcode(request.receiverShortcode().trim());
        destination.setReceiverType(request.receiverType());
        destination.setAccountReference(blankToNull(request.accountReference()));
        destination.setActive(request.active() == null || request.active());
        destination.setCreatedBy(CurrentActor.name());
        check(destination);
        B2bDestination saved = destinations.save(destination);
        B2bDestinationDto dto = B2bDestinationDto.from(saved);
        audit.record(AUDIT_CREATED, ENTITY, saved.getUuid().toString(), null, dto);
        return dto;
    }

    @Transactional
    public B2bDestinationDto update(UUID shortcodeUuid, UUID destinationUuid, UpdateB2bDestinationRequest request) {
        B2bDestination destination = find(shortcodeUuid, destinationUuid);
        B2bDestinationDto before = B2bDestinationDto.from(destination);
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new InvalidRequestException("name", "is required");
            }
            if (destinations.nameTaken(shortcodeUuid, name, destinationUuid)) {
                throw new ConflictException("This shortcode already has a destination called " + name);
            }
            destination.setName(name);
        }
        if (request.receiverShortcode() != null) {
            destination.setReceiverShortcode(request.receiverShortcode().trim());
        }
        if (request.receiverType() != null) {
            destination.setReceiverType(request.receiverType());
        }
        if (request.accountReference() != null) {
            destination.setAccountReference(blankToNull(request.accountReference()));
        }
        if (request.active() != null) {
            destination.setActive(request.active());
        }
        destination.setUpdatedBy(CurrentActor.name());
        check(destination);
        B2bDestinationDto after = B2bDestinationDto.from(destinations.save(destination));
        audit.record(AUDIT_UPDATED, ENTITY, destinationUuid.toString(), before, after);
        return after;
    }

    /** Soft delete: transfers already sent keep pointing at it. */
    @Transactional
    public void delete(UUID shortcodeUuid, UUID destinationUuid) {
        B2bDestination destination = find(shortcodeUuid, destinationUuid);
        B2bDestinationDto before = B2bDestinationDto.from(destination);
        destinations.delete(destination);
        audit.record(AUDIT_DELETED, ENTITY, destinationUuid.toString(), before, null);
    }

    B2bDestination find(UUID shortcodeUuid, UUID destinationUuid) {
        return destinations.findByUuidAndShortcodeUuid(destinationUuid, shortcodeUuid)
                .orElseThrow(() -> new NotFoundException("B2B destination not found"));
    }

    // BusinessPayBill credits an account at the receiving paybill; a till (BusinessBuyGoods) has none to give.
    private static void check(B2bDestination destination) {
        if (destination.getReceiverType() == B2bReceiverType.PAYBILL
                && !StringUtils.hasText(destination.getAccountReference())) {
            throw new InvalidRequestException("account_reference", "is required for a paybill");
        }
    }

    private void requireShortcode(UUID shortcodeUuid) {
        if (shortcodes.findByUuid(shortcodeUuid).isEmpty()) {
            throw new NotFoundException("Shortcode not found");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
