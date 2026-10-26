package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.B2BResult;
import com.sarafrika.apps.mpesaservice.dtos.B2BTransferResult;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.AttachCallbackRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.AttachCandidateDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.callbacks.CallbackDetailDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.models.CallbackInboxEntry;
import com.sarafrika.apps.mpesaservice.models.ProductPayout;
import com.sarafrika.apps.mpesaservice.models.ReversalRequest;
import com.sarafrika.apps.mpesaservice.repositories.CallbackInboxRepository;
import com.sarafrika.apps.mpesaservice.services.approvals.reversal.ReversalCallbackService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.daraja.products.ProductPayoutService;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.services.treasury.B2bTransferService;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Finance attaches a B2B, reversal, Tax Remittance or Pochi result nothing matched (Safaricom never gave us
 * its ids, or the result beat them) to the record it belongs to, after checking the statement. Nothing is resent. */
@Service
@RequiredArgsConstructor
public class CallbackAttachService {

    static final String AUDIT_ACTION = "CALLBACK_ATTACHED";
    static final String NOT_ATTACHABLE = "Only a failed B2B, reversal, Tax Remittance or Pochi result with a stored "
            + "body can be attached.";

    private final CallbackInboxRepository repository;
    private final CallbackInboxService inbox;
    private final CallbackInboxRecorder recorder;
    private final B2bTransferService transfers;
    private final ReversalCallbackService reversals;
    private final ProductPayoutService productPayouts;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    /** Open transfers or reversals the entry could belong to, those that fit its amount first. */
    public List<AttachCandidateDto> candidates(UUID uuid) {
        CallbackInboxEntry entry = inbox.find(uuid);
        if (!CallbackInboxService.attachable(entry)) {
            return List.of();
        }
        if (entry.getKind() == CallbackKind.B2B_RESULT) {
            B2BResult result = b2b(entry);
            return transfers.attachable().stream()
                    .map(t -> new AttachCandidateDto(t.getUuid(), "B2B_TRANSFER",
                            "Sweep " + t.getReference() + " to " + t.getReceiverShortcode(), t.getAmount(),
                            t.getStatus().name(), t.isOutcomeUnknown(), transfers.fits(t, result),
                            Times.offset(t.getCreatedAt())))
                    .sorted(Comparator.comparing(AttachCandidateDto::fits).reversed())
                    .toList();
        }
        DarajaFeature product = product(entry.getKind());
        if (product != null) {
            JsonNode body = tree(entry);
            boolean visible = PersonalData.visible();
            return productPayouts.attachable(product).stream()
                    .map(p -> new AttachCandidateDto(p.getUuid(), product.name(), productLabel(p, visible),
                            p.getAmount(), p.getStatus().name(), p.isOutcomeUnknown(),
                            ProductPayoutService.fits(p, body), Times.offset(p.getCreatedAt())))
                    .sorted(Comparator.comparing(AttachCandidateDto::fits).reversed())
                    .toList();
        }
        JsonNode result = reversalResult(entry);
        return reversals.attachable().stream()
                .map(r -> new AttachCandidateDto(r.getUuid(), "REVERSAL", "Reversal of " + r.getTransactionId(),
                        r.getAmount(), r.getStatus().name(), r.getConversationId() == null,
                        ReversalCallbackService.fits(r, result),
                        r.getCreatedAt() == null ? null : r.getCreatedAt().atZone(ZoneId.systemDefault())
                                .toOffsetDateTime()))
                .sorted(Comparator.comparing(AttachCandidateDto::fits).reversed())
                .toList();
    }

    public CallbackDetailDto attach(UUID uuid, AttachCallbackRequest request) {
        CallbackInboxEntry entry = inbox.find(uuid);
        if (!CallbackInboxService.attachable(entry)) {
            throw new ConflictException(NOT_ATTACHABLE);
        }
        String previousError = entry.getError();
        if (repository.claimForReplay(uuid, inbox.now(), inbox.stuckBefore()) == 0) {
            throw new ConflictException("Someone else is replaying or attaching this callback.");
        }
        UUID paymentUuid = null;
        try {
            DarajaFeature product = product(entry.getKind());
            if (entry.getKind() == CallbackKind.B2B_RESULT) {
                transfers.attachResult(request.targetUuid(), b2b(entry), entry.getBody());
            } else if (product != null) {
                productPayouts.attachResult(product, request.targetUuid(), tree(entry), entry.getBody());
            } else {
                ReversalRequest reversal = reversals.attachResult(request.targetUuid(), tree(entry),
                        entry.getBody());
                paymentUuid = reversal.getPaymentUuid();
            }
        } catch (RuntimeException ex) {
            CallbackInboxEntry back = inbox.find(uuid);
            back.setStatus(CallbackStatus.FAILED);
            back.setError(previousError);
            repository.save(back);
            throw ex;
        }

        CallbackInboxEntry attached = inbox.find(uuid);
        attached.setStatus(CallbackStatus.REPLAYED);
        attached.setError(null);
        if (paymentUuid != null && attached.getPaymentUuid() == null) {
            attached.setPaymentUuid(paymentUuid);
        }
        recorder.link(attached);
        CallbackInboxEntry saved = repository.save(attached);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", saved.getStatus().name());
        after.put("kind", saved.getKind().name());
        after.put("target_uuid", request.targetUuid().toString());
        after.put("note", request.note().trim());
        audit.record(AUDIT_ACTION, "CALLBACK", uuid.toString(), Map.of("status", CallbackStatus.FAILED.name()), after);
        return inbox.detail(saved);
    }

    private B2BResult b2b(CallbackInboxEntry entry) {
        try {
            B2BTransferResult parsed = objectMapper.readValue(entry.getBody(), B2BTransferResult.class);
            if (parsed == null || parsed.result() == null) {
                throw new ConflictException("The stored body has no B2B Result.");
            }
            return parsed.result();
        } catch (ConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConflictException("The stored body does not read as a B2B result.");
        }
    }

    private static DarajaFeature product(CallbackKind kind) {
        return switch (kind) {
            case TAX_REMITTANCE_RESULT -> DarajaFeature.TAX_REMITTANCE;
            case POCHI_RESULT -> DarajaFeature.BUSINESS_TO_POCHI;
            default -> null;
        };
    }

    private static String productLabel(ProductPayout payout, boolean visible) {
        if (payout.getProduct() == DarajaFeature.TAX_REMITTANCE) {
            return "Tax remittance PRN " + payout.getAccountReference();
        }
        return "Pochi payment to " + (visible ? payout.getPartyB() : PersonalData.maskPhone(payout.getPartyB()));
    }

    private JsonNode tree(CallbackInboxEntry entry) {
        try {
            return objectMapper.readTree(entry.getBody());
        } catch (Exception ex) {
            throw new ConflictException("The stored body does not read as JSON.");
        }
    }

    private JsonNode reversalResult(CallbackInboxEntry entry) {
        JsonNode body = tree(entry);
        return body.has("Result") ? body.get("Result") : body;
    }
}
