package com.sarafrika.apps.mpesaservice.services.reconciliation;

import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionKind;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Matches Safaricom's lines with our ledger on the receipt number. Charge, reversal and internal transfer lines
 * are counted but never expected in the ledger; a receipt reversed at Safaricom is never offered for backfill. */
public final class ReconciliationMatcher {

    public record Markers(List<String> charges, List<String> reversals, List<String> transfers) {
        boolean any(List<String> markers, String text) {
            return markers.stream().anyMatch(m -> !m.isBlank() && text.contains(m.toLowerCase(Locale.ROOT)));
        }
    }

    /** One exception to store; line is null for MISSING_IN_STATEMENT. */
    public record Draft(ReconciliationExceptionKind kind, String direction, String receipt, BigDecimal statementAmount,
                        BigDecimal ledgerAmount, List<LedgerEntry> ledger, StatementLine line, String reversedBy,
                        String suggestedFix) {
    }

    public record Result(int statementLines, int skippedLines, int chargeLines, int reversalLines, int ledgerRecords,
                         int matchedCount, BigDecimal matchedValue, BigDecimal paidInTotal, BigDecimal withdrawnTotal,
                         BigDecimal chargesTotal, List<Draft> exceptions) {

        public BigDecimal exceptionValue() {
            return exceptions.stream()
                    .map(d -> d.statementAmount() != null ? d.statementAmount() : d.ledgerAmount())
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    static final String FIX_BACKFILL = "The C2B confirmation never reached us; backfill the deposit so the product is told";
    static final String FIX_UNKNOWN_OUT = "Money left the account without a record here; find who sent it, then mark it "
            + "resolved with a note";
    static final String FIX_NOT_SETTLED = "Our record is %s but Safaricom completed it; query its status";
    static final String FIX_REVERSED = "Reversed at Safaricom by %s, so nothing is owed; mark it resolved";
    static final String FIX_NOT_IN_STATEMENT = "Not on Safaricom's record for this period; query its status before the "
            + "next run";
    static final String FIX_AMOUNT = "Compare the raw callback with the statement line, then mark it resolved with a note";
    static final String FIX_DIRECTION = "Safaricom and our ledger disagree on the direction; review both, then mark it "
            + "resolved";
    static final String FIX_DUPLICATE = "Same receipt saved %d times; merge and keep the first";

    private ReconciliationMatcher() {
    }

    /** ledgerByReceipt holds every live row for the statement's receipts; inPeriod the settled rows in the period. */
    public static Result match(List<StatementLine> lines, LocalDateTime start, LocalDateTime end,
                               List<LedgerEntry> ledgerByReceipt, List<LedgerEntry> inPeriod, Markers markers) {
        int skipped = 0;
        int charges = 0;
        int reversals = 0;
        BigDecimal paidIn = BigDecimal.ZERO;
        BigDecimal withdrawn = BigDecimal.ZERO;
        BigDecimal chargesTotal = BigDecimal.ZERO;
        Map<String, String> reversedBy = new HashMap<>();
        Set<String> onStatement = new HashSet<>();
        Map<String, List<StatementLine>> main = new LinkedHashMap<>();

        for (StatementLine line : lines) {
            if (!line.completed()) {
                skipped++;
                continue;
            }
            String text = line.description();
            boolean charge = markers.any(markers.charges(), text);
            boolean reversal = !charge && markers.any(markers.reversals(), text);
            boolean transfer = !charge && !reversal && markers.any(markers.transfers(), text);
            if (!charge && !reversal && !transfer) {
                onStatement.add(line.receipt());
            }
            if (reversal && line.linkedTransactionId() != null) {
                reversedBy.put(line.linkedTransactionId(), line.receipt());
            }
            boolean inPeriodLine = line.completedAt() != null && !line.completedAt().isBefore(start)
                    && line.completedAt().isBefore(end);
            if (!inPeriodLine || transfer) {
                skipped++;
                continue;
            }
            paidIn = paidIn.add(line.paidIn());
            withdrawn = withdrawn.add(line.withdrawn());
            if (charge) {
                charges++;
                chargesTotal = chargesTotal.add(line.amount());
            } else if (reversal) {
                reversals++;
            } else {
                main.computeIfAbsent(line.receipt(), r -> new ArrayList<>()).add(line);
            }
        }

        Map<String, List<LedgerEntry>> byKey = group(ledgerByReceipt);
        List<Draft> drafts = new ArrayList<>();
        int matched = 0;
        BigDecimal matchedValue = BigDecimal.ZERO;

        for (Map.Entry<String, List<StatementLine>> group : main.entrySet()) {
            String receipt = group.getKey();
            StatementLine first = group.getValue().getFirst();
            BigDecimal in = group.getValue().stream().map(StatementLine::paidIn).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal out = group.getValue().stream().map(StatementLine::withdrawn).reduce(BigDecimal.ZERO, BigDecimal::add);
            String direction = in.compareTo(out) >= 0 ? "IN" : "OUT";
            BigDecimal amount = "IN".equals(direction) ? in.subtract(out) : out.subtract(in);
            List<LedgerEntry> rows = byKey.getOrDefault(receipt, List.of());
            String reversal = reversedBy.get(receipt);

            if (rows.size() > 1) {
                drafts.add(new Draft(ReconciliationExceptionKind.DUPLICATE_IN_LEDGER, direction, receipt, amount,
                        rows.getFirst().amount(), rows, first, reversal, FIX_DUPLICATE.formatted(rows.size())));
            } else if (rows.isEmpty()) {
                String fix = reversal != null ? FIX_REVERSED.formatted(reversal)
                        : "IN".equals(direction) ? FIX_BACKFILL : FIX_UNKNOWN_OUT;
                drafts.add(new Draft(ReconciliationExceptionKind.MISSING_IN_LEDGER, direction, receipt, amount, null,
                        rows, first, reversal, fix));
            } else {
                LedgerEntry row = rows.getFirst();
                if (!row.settled()) {
                    drafts.add(new Draft(ReconciliationExceptionKind.MISSING_IN_LEDGER, direction, receipt, amount,
                            row.amount(), rows, first, reversal,
                            reversal != null ? FIX_REVERSED.formatted(reversal) : FIX_NOT_SETTLED.formatted(row.status())));
                } else if (!row.direction().equals(direction)) {
                    drafts.add(new Draft(ReconciliationExceptionKind.AMOUNT_MISMATCH, direction, receipt, amount,
                            row.amount(), rows, first, reversal, FIX_DIRECTION));
                } else if (row.amount().compareTo(amount) != 0) {
                    drafts.add(new Draft(ReconciliationExceptionKind.AMOUNT_MISMATCH, direction, receipt, amount,
                            row.amount(), rows, first, reversal, FIX_AMOUNT));
                } else {
                    matched++;
                    matchedValue = matchedValue.add(amount);
                }
            }
        }

        // Settled rows in the period that Safaricom's record never mentions.
        Map<String, List<LedgerEntry>> periodByKey = group(inPeriod);
        for (Map.Entry<String, List<LedgerEntry>> group : periodByKey.entrySet()) {
            if (main.containsKey(group.getKey()) || onStatement.contains(group.getKey())) {
                continue;
            }
            List<LedgerEntry> rows = byKeyMerged(byKey, group);
            LedgerEntry row = rows.getFirst();
            if (rows.size() > 1) {
                drafts.add(new Draft(ReconciliationExceptionKind.DUPLICATE_IN_LEDGER, row.direction(), group.getKey(),
                        null, row.amount(), rows, null, null, FIX_DUPLICATE.formatted(rows.size())));
            } else {
                drafts.add(new Draft(ReconciliationExceptionKind.MISSING_IN_STATEMENT, row.direction(), group.getKey(),
                        null, row.amount(), rows, null, null, FIX_NOT_IN_STATEMENT));
            }
        }
        Set<String> ledgerRows = new HashSet<>();
        inPeriod.forEach(r -> ledgerRows.add(r.table() + ":" + r.id()));
        ledgerByReceipt.forEach(r -> ledgerRows.add(r.table() + ":" + r.id()));
        int ledgerRecords = ledgerRows.size();

        return new Result(lines.size(), skipped, charges, reversals, ledgerRecords, matched, matchedValue, paidIn,
                withdrawn, chargesTotal, drafts);
    }

    private static List<LedgerEntry> byKeyMerged(Map<String, List<LedgerEntry>> byKey,
                                                 Map.Entry<String, List<LedgerEntry>> group) {
        List<LedgerEntry> rows = new ArrayList<>(group.getValue());
        for (LedgerEntry extra : byKey.getOrDefault(group.getKey(), List.of())) {
            if (rows.stream().noneMatch(r -> same(r, extra))) {
                rows.add(extra);
            }
        }
        rows.sort(FIRST);
        return rows;
    }

    /** The first-saved row comes first: it is the one a merge keeps. */
    static final Comparator<LedgerEntry> FIRST = Comparator
            .comparing(LedgerEntry::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LedgerEntry::table).thenComparingLong(LedgerEntry::id);

    private static Map<String, List<LedgerEntry>> group(List<LedgerEntry> rows) {
        Map<String, List<LedgerEntry>> byKey = new LinkedHashMap<>();
        for (LedgerEntry row : rows) {
            List<LedgerEntry> list = byKey.computeIfAbsent(row.key(), k -> new ArrayList<>());
            if (list.stream().noneMatch(r -> same(r, row))) {
                list.add(row);
            }
        }
        byKey.values().forEach(list -> list.sort(FIRST));
        return byKey;
    }

    private static boolean same(LedgerEntry a, LedgerEntry b) {
        return a.id() == b.id() && a.table().equals(b.table());
    }
}
