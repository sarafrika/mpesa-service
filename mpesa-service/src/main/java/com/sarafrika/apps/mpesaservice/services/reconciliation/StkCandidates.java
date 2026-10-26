package com.sarafrika.apps.mpesaservice.services.reconciliation;

import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationLedger.StkCandidate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The settled, receiptless STK payments a deposit on Safaricom's record may be: same shortcode and amount,
 * requested within the configured window of the line's time, and the same phone when the line shows one
 * (statements mask the middle, 254722***111, so only the digits shown are compared). */
@Component
@RequiredArgsConstructor
public class StkCandidates {

    private final ReconciliationLedger ledger;
    private final ReconciliationProperties properties;

    public List<StkCandidate> find(UUID shortcodeUuid, StatementLine line, BigDecimal amount) {
        return line == null ? List.of() : find(shortcodeUuid, line.completedAt(), line.otherParty(), amount);
    }

    /** The same match for a deposit seen at a time, from a party shown as Safaricom shows it (a C2B MSISDN that
     * is masked or hashed fits any phone, since nothing in it can be compared). */
    public List<StkCandidate> find(UUID shortcodeUuid, LocalDateTime at, String otherParty, BigDecimal amount) {
        if (at == null) {
            return List.of();
        }
        return ledger.stkCandidates(shortcodeUuid, amount, at, properties.stkMatchWindow()).stream()
                .filter(c -> phoneFits(otherParty, c.phoneNumber())).toList();
    }

    /** False only when both numbers are known and the digits Safaricom shows differ from ours. */
    static boolean phoneFits(String otherParty, String phone) {
        if (otherParty == null || phone == null || phone.isBlank()) {
            return true;
        }
        int dash = otherParty.indexOf(" - ");
        String shown = (dash < 0 ? otherParty : otherParty.substring(0, dash)).replaceAll("\\s", "");
        if (!shown.matches("[0-9*+]{9,15}") || !shown.matches(".*\\d.*")) {
            return true; // not a phone number (a business name or till), so nothing to compare
        }
        String ours = msisdn(phone.replaceAll("\\D", ""));
        int firstStar = shown.indexOf('*');
        if (firstStar < 0) {
            return ours.equals(msisdn(shown.replaceAll("\\D", "")));
        }
        String prefix = msisdn(shown.substring(0, firstStar).replaceAll("\\D", ""));
        String suffix = shown.substring(shown.lastIndexOf('*') + 1).replaceAll("\\D", "");
        return ours.startsWith(prefix) && ours.endsWith(suffix);
    }

    private static String msisdn(String digits) {
        if (digits.startsWith("0")) {
            return "254" + digits.substring(1);
        }
        return digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("1")) ? "254" + digits : digits;
    }
}
