package com.sarafrika.apps.mpesaservice.services.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation.PullTransactionsStatusDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.PullTransactionRegistration;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.PullTransactionRegistrationRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.features.ShortcodeFeatures;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Daraja Pull Transactions (developer.safaricom.co.ke/apis/PullTransaction), only for shortcodes whose
 * PULL_TRANSACTIONS feature is on: a one-time Register, then Query for C2B rows of the last 48 hours. */
@Service
@Slf4j
@RequiredArgsConstructor
public class PullTransactionsService {

    static final String AUDIT_REGISTER = "PULL_TRANSACTIONS_REGISTERED";
    static final String OFF = "Pull Transactions is not enabled for this paybill. Enable it on the paybill's Features "
            + "tab once Safaricom has switched the product on for its app";
    // Register: ResponseStatus 1000 registered, 1001 already registered. Query: ResponseCode 1000 rows, 1001 none.
    static final Set<String> REGISTERED = Set.of("1000", "1001");
    static final String ROWS = "1000";
    static final String NO_ROWS = "1001";
    static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final MpesaDarajaService daraja;
    private final ShortcodeFeatures features;
    private final MpesaShortCodeRepository shortcodes;
    private final PullTransactionRegistrationRepository registrations;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final ReconciliationProperties properties;
    @Value("${mpesa.callback.base-url:}")
    private String callbackBaseUrl;

    /** 409 with a clear message when the shortcode's Pull Transactions feature is off. */
    public void requireEnabled(UUID shortcodeUuid) {
        if (!features.isEnabled(shortcodeUuid, DarajaFeature.PULL_TRANSACTIONS)) {
            throw new ConflictException(OFF);
        }
    }

    @Transactional(readOnly = true)
    public PullTransactionsStatusDto status(UUID shortcodeUuid) {
        shortcode(shortcodeUuid);
        boolean enabled = features.isEnabled(shortcodeUuid, DarajaFeature.PULL_TRANSACTIONS);
        return registrations.findByShortcodeUuid(shortcodeUuid).map(r -> dto(r, enabled))
                .orElseGet(() -> new PullTransactionsStatusDto(shortcodeUuid, enabled, false, null, null, null, null,
                        null, null, properties.nightly().enabled()));
    }

    /** Registers the shortcode; Safaricom's refusal is stored and returned, an unreachable Safaricom is a 502/503. */
    @Transactional
    public PullTransactionsStatusDto register(UUID shortcodeUuid, String nominatedNumber) {
        MpesaShortCode shortcode = shortcode(shortcodeUuid);
        requireEnabled(shortcodeUuid);
        String number = msisdn(nominatedNumber);
        String callbackUrl = callbackUrl();
        MpesaApiResponse<Map<String, Object>> response = daraja.registerPullTransactions(shortcode, number, callbackUrl);
        if (!response.success() || response.data() == null) {
            String detail = response.error() == null ? null : response.error().errorMessage();
            if (response.httpStatus() == 503) {
                throw UpstreamException.unavailable("Safaricom could not be reached; try again shortly");
            }
            throw UpstreamException.badGateway("Safaricom refused the Pull Transactions registration"
                    + (detail == null ? "" : " (" + detail + ")"));
        }
        Map<String, Object> body = response.data();
        PullTransactionRegistration row = registrations.findByShortcodeUuid(shortcodeUuid)
                .orElseGet(PullTransactionRegistration::new);
        String status = text(body, "ResponseStatus", "Response Status", "ResponseCode");
        row.setShortcodeUuid(shortcodeUuid);
        row.setNominatedNumber(number);
        row.setCallbackUrl(callbackUrl);
        row.setResponseStatus(status);
        row.setResponseDescription(text(body, "ResponseDescription", "Response Description", "ResponseMessage"));
        row.setResponseRefId(text(body, "ResponseRefID", "RequestID"));
        row.setRaw(json(body));
        row.setRegisteredBy(CurrentActor.name());
        boolean ok = status != null && REGISTERED.contains(status.trim());
        row.setRegistered(ok || row.isRegistered());
        if (ok) {
            row.setRegisteredAt(LocalDateTime.now());
        }
        PullTransactionRegistration saved = registrations.save(row);
        audit.record(AUDIT_REGISTER, "SHORTCODE", shortcodeUuid.toString(), null, Map.of(
                "response_status", String.valueOf(status), "registered", ok,
                "nominated_number", PersonalData.maskPhone(number)));
        return dto(saved, true);
    }

    /** Every C2B row Safaricom has for the window, paging with OffSetValue until a page is empty or repeats. */
    public List<StatementLine> fetch(MpesaShortCode shortcode, LocalDateTime start, LocalDateTime end) {
        requireEnabled(shortcode.getUuid());
        boolean registered = registrations.findByShortcodeUuid(shortcode.getUuid())
                .map(PullTransactionRegistration::isRegistered).orElse(false);
        if (!registered) {
            throw new ConflictException("Register this paybill for Pull Transactions before running with it");
        }
        List<StatementLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int offset = 0;
        for (int page = 0; page < properties.pull().maxPages(); page++) {
            MpesaApiResponse<Map<String, Object>> response = daraja.queryPullTransactions(shortcode, start, end, offset);
            if (!response.success() || response.data() == null) {
                if (response.httpStatus() == 503) {
                    throw UpstreamException.unavailable("Safaricom could not be reached; try again shortly");
                }
                String detail = response.error() == null ? null : response.error().errorMessage();
                throw UpstreamException.badGateway("Safaricom refused the Pull Transactions query"
                        + (detail == null ? "" : " (" + detail + ")"));
            }
            Map<String, Object> body = response.data();
            String code = text(body, "ResponseCode", "Response Code");
            if (NO_ROWS.equals(code)) {
                break;
            }
            if (!ROWS.equals(code)) {
                throw UpstreamException.badGateway("Safaricom refused the Pull Transactions query: "
                        + text(body, "ResponseMessage", "Response Message", "errorMessage"));
            }
            List<Map<String, Object>> items = items(body.get("Response"));
            int fresh = 0;
            for (int i = 0; i < items.size(); i++) {
                StatementLine line = line(offset + i + 1, items.get(i));
                if (line != null && seen.add(line.receipt())) {
                    lines.add(line);
                    fresh++;
                }
            }
            if (fresh == 0) {
                break; // an empty page, or Safaricom repeating rows it already sent
            }
            offset += items.size();
        }
        return lines;
    }

    // Item keys per the Daraja Pull Transactions response: transactionId, trxDate, msisdn, sender,
    // transactiontype, billreference, amount, organizationname. Pull returns C2B (money in) only.
    static StatementLine line(int lineNumber, Map<String, Object> item) {
        String receipt = string(item.get("transactionId"));
        if (!StringUtils.hasText(receipt)) {
            return null;
        }
        Map<String, String> raw = new LinkedHashMap<>();
        item.forEach((k, v) -> raw.put(k, v == null ? null : String.valueOf(v)));
        String msisdn = string(item.get("msisdn"));
        if (msisdn != null && msisdn.matches("[17]\\d{8}")) {
            msisdn = "254" + msisdn;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(item.get("amount")).replace(",", "").trim()).abs();
        } catch (NumberFormatException e) {
            throw UpstreamException.badGateway("Safaricom sent an unreadable amount for " + receipt);
        }
        return new StatementLine(lineNumber, receipt.trim().toUpperCase(Locale.ROOT), time(string(item.get("trxDate"))),
                string(item.get("transactiontype")), "Completed", amount, BigDecimal.ZERO,
                string(item.get("transactiontype")), msisdn, null, string(item.get("billreference")), raw);
    }

    // Documented samples show "2020-08-05T10:13:00Z"; whether that is UTC or EAT marked Z is unverified, so the
    // local part is taken as East Africa Time, as everywhere else in Daraja.
    static LocalDateTime time(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return StatementCsvParser.time(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> items(Object response) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (response instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    items.add((Map<String, Object>) map);
                } else if (entry instanceof List<?>) {
                    items.addAll(items(entry)); // Response is documented as an array of arrays
                }
            }
        }
        return items;
    }

    private PullTransactionsStatusDto dto(PullTransactionRegistration r, boolean enabled) {
        String number = PersonalData.visible() ? r.getNominatedNumber() : PersonalData.maskPhone(r.getNominatedNumber());
        return new PullTransactionsStatusDto(r.getShortcodeUuid(), enabled, r.isRegistered(), number, r.getCallbackUrl(),
                r.getResponseStatus(), r.getResponseDescription(), r.getRegisteredBy(), Times.offset(r.getRegisteredAt()),
                properties.nightly().enabled());
    }

    private String callbackUrl() {
        if (StringUtils.hasText(properties.pull().callbackUrl())) {
            return properties.pull().callbackUrl();
        }
        return callbackBaseUrl.replaceAll("/+$", "") + "/pull-transactions/callback";
    }

    /** 0722000000, 722000000 and +254722000000 all become 254722000000. */
    static String msisdn(String number) {
        String digits = number.replaceAll("\\D", "");
        if (digits.startsWith("0")) {
            return "254" + digits.substring(1);
        }
        return digits.startsWith("254") ? digits : "254" + digits;
    }

    private MpesaShortCode shortcode(UUID uuid) {
        return shortcodes.findByUuid(uuid).orElseThrow(() -> new NotFoundException("Shortcode not found"));
    }

    private static String text(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? null : String.valueOf(value).trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
