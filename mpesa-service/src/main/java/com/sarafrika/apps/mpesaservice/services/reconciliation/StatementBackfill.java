package com.sarafrika.apps.mpesaservice.services.reconciliation;

import com.sarafrika.apps.mpesaservice.dtos.C2BConfirmationCallback;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a statement line into the C2B confirmation Safaricom never delivered, so the deposit is stored and
 * announced through the normal confirmation path. Masked parts (254790***760, Alex ***) are kept as they are. */
public final class StatementBackfill {

    // Details read like "Pay Bill Online from 254790***760 - Alex *** Acc. 4GPWHB" (presta-ui c2b-import-template.csv).
    private static final Pattern ACCOUNT = Pattern.compile("\\bAcc\\.\\s*(\\S+)");
    private static final Pattern PARTY_SPLIT = Pattern.compile("\\s+[-\u2013]\\s+");
    private static final DateTimeFormatter TRANS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private StatementBackfill() {
    }

    public static C2BConfirmationCallback callback(StatementLine line, String businessShortCode, BigDecimal amount) {
        String[] party = party(line.otherParty());
        List<String> names = party.length > 1 ? Arrays.stream(party[1].trim().split("\\s+"))
                .filter(n -> !n.isBlank() && !n.contains("*")).toList() : List.of();
        return new C2BConfirmationCallback(
                line.reasonType() != null ? line.reasonType() : "Pay Bill",
                line.receipt(),
                line.completedAt() == null ? null : line.completedAt().format(TRANS_TIME),
                amount,
                businessShortCode,
                accountReference(line),
                null, null, null,
                party.length > 0 && !party[0].isBlank() ? party[0].trim() : null,
                names.isEmpty() ? null : names.getFirst(),
                names.size() > 2 ? names.get(1) : null,
                names.size() > 1 ? names.getLast() : null);
    }

    /** A/C No. when the export has it, else the text after "Acc." in Details. */
    public static String accountReference(StatementLine line) {
        if (line.accountNumber() != null && !line.accountNumber().isBlank()) {
            return line.accountNumber().trim();
        }
        if (line.details() != null) {
            Matcher m = ACCOUNT.matcher(line.details());
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static String[] party(String otherParty) {
        return otherParty == null || otherParty.isBlank() ? new String[0] : PARTY_SPLIT.split(otherParty.trim(), 2);
    }
}
