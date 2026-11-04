package com.sarafrika.apps.mpesaservice.services.treasury;

import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.B2bDestinationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.CreateB2bDestinationRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.treasury.UpdateB2bDestinationRequest;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.B2bDestination;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.B2bDestinationRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bReceiverType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** The paybills, tills and own B2C shortcodes a shortcode may move money to; every change is audited. */
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
        Page<B2bDestination> page = destinations.findForShortcode(shortcodeUuid, active, pageable);
        List<String> numbers = page.getContent().stream().filter(d -> d.getReceiverType().topUp())
                .map(B2bDestination::getReceiverShortcode).distinct().toList();
        Map<String, String> names = numbers.isEmpty() ? Map.of() : shortcodes.findByShortcodeIn(numbers).stream()
                .filter(c -> c.getBusinessName() != null)
                .collect(Collectors.toMap(MpesaShortCode::getShortcode, MpesaShortCode::getBusinessName, (a, b) -> a));
        return page.map(d -> B2bDestinationDto.from(d, d.getReceiverType().topUp()
                ? names.get(d.getReceiverShortcode()) : null));
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
        MpesaShortCode receiver = check(destination);
        B2bDestination saved = destinations.save(destination);
        B2bDestinationDto dto = view(saved, receiver);
        audit.record(AUDIT_CREATED, ENTITY, saved.getUuid().toString(), null, dto);
        return dto;
    }

    @Transactional
    public B2bDestinationDto update(UUID shortcodeUuid, UUID destinationUuid, UpdateB2bDestinationRequest request) {
        B2bDestination destination = find(shortcodeUuid, destinationUuid);
        B2bDestinationDto before = view(destination, null);
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
        MpesaShortCode receiver = check(destination);
        B2bDestinationDto after = view(destinations.save(destination), receiver);
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
    // A top-up (BusinessPayToBulk) must reach one of our own active B2C shortcodes, never the source itself.
    private MpesaShortCode check(B2bDestination destination) {
        if (destination.getReceiverType() == B2bReceiverType.PAYBILL
                && !StringUtils.hasText(destination.getAccountReference())) {
            throw new InvalidRequestException("account_reference", "is required for a paybill");
        }
        if (!destination.getReceiverType().topUp()) {
            return null;
        }
        destination.setAccountReference(null);
        if (!destination.isActive() && destination.getId() != null) {
            return null; // Deactivating a top-up whose receiver went away must still work; sends re-check it.
        }
        return topUpReceiver(shortcodes, destination.getShortcodeUuid(), destination.getReceiverShortcode())
                .orElseThrow(() -> new InvalidRequestException("receiver_shortcode",
                        topUpRefusal(shortcodes, destination.getShortcodeUuid(), destination.getReceiverShortcode())));
    }

    /** Our own active B2C shortcode with this number, unless it is the source itself. */
    static Optional<MpesaShortCode> topUpReceiver(MpesaShortCodeRepository shortcodes, UUID sourceUuid,
                                                  String number) {
        return shortcodes.findByShortcode(number)
                .filter(c -> c.getShortcodeType() == ShortcodeType.B2C)
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .filter(c -> !c.getUuid().equals(sourceUuid));
    }

    /** Why this number cannot be topped up from the source, as the field error says it. */
    static String topUpRefusal(MpesaShortCodeRepository shortcodes, UUID sourceUuid, String number) {
        Optional<MpesaShortCode> found = shortcodes.findByShortcode(number);
        if (found.isEmpty()) {
            return "is not one of our shortcodes; a B2C top-up goes to our own B2C shortcode";
        }
        MpesaShortCode code = found.get();
        if (code.getUuid().equals(sourceUuid)) {
            return "is this shortcode itself; a top-up must go to another shortcode";
        }
        if (code.getShortcodeType() != ShortcodeType.B2C) {
            return "is not a B2C shortcode; only B2C shortcodes take a top-up";
        }
        return "is not active; activate the B2C shortcode first";
    }

    private B2bDestinationDto view(B2bDestination destination, MpesaShortCode receiver) {
        if (!destination.getReceiverType().topUp()) {
            return B2bDestinationDto.from(destination);
        }
        MpesaShortCode code = receiver != null ? receiver
                : shortcodes.findByShortcode(destination.getReceiverShortcode()).orElse(null);
        return B2bDestinationDto.from(destination, code == null ? null : code.getBusinessName());
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
