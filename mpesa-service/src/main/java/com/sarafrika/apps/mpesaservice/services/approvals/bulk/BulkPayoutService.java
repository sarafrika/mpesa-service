package com.sarafrika.apps.mpesaservice.services.approvals.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarafrika.apps.mpesaservice.dtos.PayoutFloatDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.BulkPayoutRowDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.BulkPayoutUploadDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.approvals.PreCheckDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.PayoutBatch;
import com.sarafrika.apps.mpesaservice.models.PayoutBatchItem;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.PayoutBatchItemRepository;
import com.sarafrika.apps.mpesaservice.repositories.PayoutBatchRepository;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalGateway;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalProperties;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalSubmission;
import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalTypes;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeCapabilities;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Checks an uploaded payout CSV row by row and, unless it is a preview, turns the valid rows into a batch
 * behind one BULK_PAYOUT approval. A row is refused for a bad phone or amount, an amount outside the
 * paybill's limits, or a reference repeated in the file, already queued, or already paid (see below). */
@Service
@RequiredArgsConstructor
public class BulkPayoutService {

    static final String DARAJA_CALL = "POST /mpesa/b2c/v3/paymentrequest · BusinessPayment";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final MpesaShortCodeRepository shortcodes;
    private final PayoutBatchRepository batches;
    private final PayoutBatchItemRepository items;
    private final ApprovalGateway gateway;
    private final MpesaPayoutService payouts;
    private final ApprovalProperties properties;
    private final ObjectMapper mapper;

    public record Upload(byte[] content, String fileName, UUID shortcodeUuid, String title, String reason,
                         boolean preview) {
    }

    private record Checked(BulkPayoutCsv.Row row, String phone, BigDecimal amount, List<String> problems) {
    }

    @Transactional
    public BulkPayoutUploadDto upload(Upload upload) {
        if (upload.content() == null || upload.content().length == 0) {
            throw new InvalidRequestException("file", "is required");
        }
        if (upload.content().length > properties.bulkMaxFileSize().toBytes()) {
            throw new InvalidRequestException("file", "is larger than " + properties.bulkMaxFileSize().toKilobytes()
                    + " KB; split it into smaller batches");
        }
        if (!StringUtils.hasText(upload.title()) || upload.title().trim().length() > 200) {
            throw new InvalidRequestException("title", "is required (at most 200 characters)");
        }
        if (!upload.preview() && (!StringUtils.hasText(upload.reason()) || upload.reason().trim().length() > 1000)) {
            throw new InvalidRequestException("reason", "is required (at most 1000 characters)");
        }
        MpesaShortCode shortcode = shortcodes.findByUuid(upload.shortcodeUuid())
                .orElseThrow(() -> new NotFoundException("Shortcode not found"));
        ShortcodeCapabilities.require(shortcode, ShortcodeCapability.B2C_PAYOUT);
        if (!Boolean.TRUE.equals(shortcode.getIsActive())) {
            throw new ConflictException("Paybill " + shortcode.getShortcode() + " is suspended; it cannot pay out.");
        }
        if (!StringUtils.hasText(shortcode.getInitiatorName())) {
            throw new ConflictException("Paybill " + shortcode.getShortcode() + " has no B2C initiator configured.");
        }

        List<Checked> checked = check(BulkPayoutCsv.parse(upload.content(), properties.bulkMaxRows()), shortcode);
        List<Checked> valid = checked.stream().filter(c -> c.problems().isEmpty()).toList();
        BigDecimal total = valid.stream().map(Checked::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean full = PersonalData.visible();
        List<BulkPayoutRowDto> rows = checked.stream().map(c -> new BulkPayoutRowDto(c.row().line(),
                full ? phoneOf(c) : PersonalData.maskPhone(phoneOf(c)), c.amount(), c.row().reference(),
                full ? c.row().name() : PersonalData.shortName(c.row().name()), c.problems().isEmpty(),
                c.problems())).toList();
        int invalid = checked.size() - valid.size();
        if (upload.preview()) {
            return new BulkPayoutUploadDto(true, rows, valid.size(), invalid, total, null, null);
        }
        if (valid.isEmpty()) {
            throw new InvalidRequestException("file", "has no valid rows to pay");
        }

        String title = upload.title().trim();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("title", title);
        payload.put("daraja_call", DARAJA_CALL + " × " + valid.size());
        payload.set("pre_checks", mapper.valueToTree(preChecks(shortcode, valid.size(), invalid, total)));
        payload.put("file_name", upload.fileName());
        payload.put("item_count", valid.size());
        UUID approval = gateway.submit(new ApprovalSubmission(ApprovalTypes.BULK_PAYOUT, shortcode.getUuid(), total,
                upload.reason().trim(), payload));

        PayoutBatch batch = new PayoutBatch();
        batch.setApprovalUuid(approval);
        batch.setShortcodeUuid(shortcode.getUuid());
        batch.setTitle(title);
        batch.setFileName(upload.fileName() == null ? null : clip(upload.fileName(), 255));
        batch.setItemCount(valid.size());
        batch.setInvalidCount(invalid);
        batch.setTotalAmount(total);
        batch.setCreatedBy(CurrentActor.name());
        PayoutBatch saved = batches.saveAndFlush(batch);
        try {
            valid.forEach(c -> items.claimReference(c.row().reference(), saved.getId()));
        } catch (DataIntegrityViolationException ex) {
            // Another upload queued one of these references since the check above; nothing is kept.
            throw new ConflictException("A reference in this file was just queued in another bulk payout; "
                    + "preview the file again.");
        }
        items.saveAll(valid.stream().map(c -> {
            PayoutBatchItem item = new PayoutBatchItem();
            item.setBatchId(saved.getId());
            item.setLineNumber(c.row().line());
            item.setPhoneNumber(c.phone());
            item.setAmount(c.amount());
            item.setReference(c.row().reference());
            item.setName(c.row().name());
            return item;
        }).toList());
        return new BulkPayoutUploadDto(false, rows, valid.size(), invalid, total, approval, saved.getUuid());
    }

    private List<Checked> check(List<BulkPayoutCsv.Row> rows, MpesaShortCode shortcode) {
        List<String> references = rows.stream().map(BulkPayoutCsv.Row::reference)
                .filter(StringUtils::hasText).distinct().toList();
        Map<String, String> used = usedReferences(references);
        Set<String> queued = Set.copyOf(chunks(references).stream().flatMap(c -> items.findOpenReferences(c).stream())
                .toList());
        Map<String, Integer> firstLine = new HashMap<>();
        List<Checked> checked = new ArrayList<>();
        for (BulkPayoutCsv.Row row : rows) {
            List<String> problems = new ArrayList<>();
            String phone = KenyanPhones.normalise(row.phone()).orElse(null);
            if (phone == null) {
                problems.add(row.phone() == null ? "phone is missing"
                        : "phone is not a Kenyan mobile number (07…, 01… or 254…)");
            }
            BigDecimal amount = amount(row.amount(), problems);
            if (amount != null) {
                if (shortcode.getMinAmount() != null && amount.compareTo(shortcode.getMinAmount()) < 0) {
                    problems.add("amount is below this paybill's minimum of KES " + money(shortcode.getMinAmount()));
                }
                if (shortcode.getMaxAmount() != null && amount.compareTo(shortcode.getMaxAmount()) > 0) {
                    problems.add("amount is above this paybill's maximum of KES " + money(shortcode.getMaxAmount()));
                }
            }
            String reference = row.reference();
            if (reference == null) {
                problems.add("reference is missing");
            } else if (reference.length() > 100) {
                problems.add("reference must be at most 100 characters");
            } else {
                Integer earlier = firstLine.putIfAbsent(reference, row.line());
                if (earlier != null) {
                    problems.add("reference repeats line " + earlier);
                }
                if (used.containsKey(reference)) {
                    problems.add("reference was already paid out on " + used.get(reference));
                }
                if (queued.contains(reference)) {
                    problems.add("reference is already in a bulk payout waiting or running");
                }
            }
            if (row.name() != null && row.name().length() > 100) {
                problems.add("name must be at most 100 characters");
            }
            checked.add(new Checked(row, phone, amount, problems));
        }
        return checked;
    }

    // The console's references at any time (a repeat would return that payout), anyone's this month.
    private Map<String, String> usedReferences(List<String> references) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Map<String, String> used = new HashMap<>();
        for (List<String> chunk : chunks(references)) {
            for (Object[] hit : items.findUsedReferences(chunk, properties.consoleClientId(), monthStart)) {
                LocalDateTime at = (LocalDateTime) hit[1];
                used.putIfAbsent((String) hit[0], at == null ? "an earlier date" : at.format(DAY));
            }
        }
        return used;
    }

    private List<PreCheckDto> preChecks(MpesaShortCode shortcode, int valid, int invalid, BigDecimal total) {
        List<PreCheckDto> checks = new ArrayList<>();
        checks.add(invalid == 0 ? PreCheckDto.ok(valid + " rows valid")
                : PreCheckDto.warn(valid + " rows valid; " + invalid + " invalid " + (invalid == 1 ? "row" : "rows")
                        + " left out"));
        PayoutFloatDto floatNow = payouts.latestFloat(shortcode.getUuid());
        BigDecimal utility = floatNow.utilityAccountAvailableFunds();
        if (utility == null) {
            checks.add(PreCheckDto.warn("No float reading for this paybill yet; check its balance before approving"));
        } else if (utility.compareTo(total) < 0) {
            checks.add(PreCheckDto.warn("Float KES " + money(utility) + " is below the batch total; top up or split "
                    + "the batch"));
        } else {
            checks.add(PreCheckDto.ok("Float KES " + money(utility) + " covers the batch total"));
        }
        checks.add(PreCheckDto.ok("No reference already paid this month or queued in another batch"));
        return checks;
    }

    private static BigDecimal amount(String raw, List<String> problems) {
        if (raw == null) {
            problems.add("amount is missing");
            return null;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(raw.replace(",", "").replace("KES", "").trim());
        } catch (NumberFormatException ex) {
            problems.add("amount is not a number");
            return null;
        }
        if (amount.signum() <= 0) {
            problems.add("amount must be more than 0");
            return null;
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            problems.add("amount must be whole shillings");
            return null;
        }
        return amount.setScale(2);
    }

    private static String phoneOf(Checked checked) {
        return checked.phone() != null ? checked.phone() : checked.row().phone();
    }

    private static List<List<String>> chunks(List<String> values) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < values.size(); i += 500) {
            chunks.add(values.subList(i, Math.min(values.size(), i + 500)));
        }
        return chunks;
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
}
