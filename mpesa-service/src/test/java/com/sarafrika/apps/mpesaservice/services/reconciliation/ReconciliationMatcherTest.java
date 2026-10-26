package com.sarafrika.apps.mpesaservice.services.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationMatcher.Draft;
import com.sarafrika.apps.mpesaservice.utils.enums.ReconciliationExceptionKind;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReconciliationMatcherTest {

    static final LocalDateTime START = LocalDateTime.of(2026, 10, 25, 0, 0);
    static final LocalDateTime END = START.plusDays(1);
    static final ReconciliationMatcher.Markers MARKERS = new ReconciliationMatcher.Markers(
            List.of("charge"), List.of("reversal"), List.of("settlement account"));

    @Test
    void classifiesTheFixtureStatementAgainstTheLedger() throws Exception {
        List<StatementLine> lines = StatementCsvParser.parse(StatementCsvParserTest.fixture("org-statement-utility.csv"),
                100).lines();
        LedgerEntry matched = in(1, "TJP9X2K1AA", "5000.00", "SUCCESS", START.plusHours(9));
        LedgerEntry payout = out(2, "TJP9X2K2CC", "3000.00");
        LedgerEntry notOnStatement = in(3, "TJQ0000ZZZ", "1200.00", "SUCCESS", START.plusHours(16));

        ReconciliationMatcher.Result result = ReconciliationMatcher.match(lines, START, END,
                List.of(matched, payout), List.of(matched, payout, notOnStatement), MARKERS);

        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.matchedValue()).isEqualByComparingTo("8000.00");
        assertThat(result.chargeLines()).isEqualTo(1);
        assertThat(result.chargesTotal()).isEqualByComparingTo("15.00");
        assertThat(result.reversalLines()).isEqualTo(1);
        assertThat(result.skippedLines()).isEqualTo(1); // the Failed line
        assertThat(result.ledgerRecords()).isEqualTo(3);
        Map<String, Draft> byReceipt = byReceipt(result);
        assertThat(byReceipt.get("TJP9X2K0BB").kind()).isEqualTo(ReconciliationExceptionKind.MISSING_IN_LEDGER);
        assertThat(byReceipt.get("TJP9X2K0BB").suggestedFix()).isEqualTo(ReconciliationMatcher.FIX_BACKFILL);
        // Paid in, then reversed at Safaricom: nothing is owed, so it is never offered for backfill.
        Draft reversed = byReceipt.get("TJP9X2K4EE");
        assertThat(reversed.kind()).isEqualTo(ReconciliationExceptionKind.MISSING_IN_LEDGER);
        assertThat(reversed.reversedBy()).isEqualTo("TJP9X2K5FF");
        assertThat(reversed.suggestedFix()).contains("Reversed at Safaricom by TJP9X2K5FF");
        assertThat(byReceipt).doesNotContainKey("TJP9X2K5FF").doesNotContainKey("TJP9X2K3DD");
        assertThat(byReceipt.get("TJQ0000ZZZ").kind()).isEqualTo(ReconciliationExceptionKind.MISSING_IN_STATEMENT);
        assertThat(result.exceptions()).hasSize(3);
    }

    @Test
    void flagsAmountMismatchesDuplicatesAndUnsettledRows() {
        List<StatementLine> lines = List.of(line("R1", "10000.00"), line("R2", "3000.00"), line("R3", "800.00"));
        LedgerEntry wrongAmount = in(1, "R1", "1000.00", "SUCCESS", START.plusHours(1));
        LedgerEntry first = in(2, "R2", "3000.00", "SUCCESS", START.plusHours(1));
        LedgerEntry again = in(3, "r2 ", "3000.00", "SUCCESS", START.plusHours(2));
        LedgerEntry pending = in(4, "R3", "800.00", "FAILED", START.plusHours(1));

        ReconciliationMatcher.Result result = ReconciliationMatcher.match(lines, START, END,
                List.of(wrongAmount, first, again, pending), List.of(wrongAmount, first, again), MARKERS);

        Map<String, Draft> byReceipt = byReceipt(result);
        assertThat(byReceipt.get("R1").kind()).isEqualTo(ReconciliationExceptionKind.AMOUNT_MISMATCH);
        assertThat(byReceipt.get("R1").statementAmount()).isEqualByComparingTo("10000.00");
        assertThat(byReceipt.get("R1").ledgerAmount()).isEqualByComparingTo("1000.00");
        Draft duplicate = byReceipt.get("R2");
        assertThat(duplicate.kind()).isEqualTo(ReconciliationExceptionKind.DUPLICATE_IN_LEDGER);
        assertThat(duplicate.ledger()).extracting(LedgerEntry::id).containsExactly(2L, 3L); // first saved first
        assertThat(byReceipt.get("R3").kind()).isEqualTo(ReconciliationExceptionKind.MISSING_IN_LEDGER);
        assertThat(byReceipt.get("R3").suggestedFix()).contains("FAILED");
        assertThat(result.matchedCount()).isZero();
        assertThat(result.exceptionValue()).isEqualByComparingTo("13800.00");
    }

    @Test
    void ignoresLinesOutsideThePeriodButNeverCallsTheirLedgerRowsMissing() {
        StatementLine late = new StatementLine(2, "R9", END.plusMinutes(1), "Pay Bill", "Completed",
                new BigDecimal("50"), BigDecimal.ZERO, "Pay Bill Online", null, null, null, Map.of());
        LedgerEntry recordedEarly = in(1, "R9", "50", "SUCCESS", END.minusMinutes(1));

        ReconciliationMatcher.Result result = ReconciliationMatcher.match(List.of(late), START, END,
                List.of(recordedEarly), List.of(recordedEarly), MARKERS);

        assertThat(result.skippedLines()).isEqualTo(1);
        assertThat(result.exceptions()).isEmpty();
    }

    @Test
    void internalTransfersAreNotExpectedInTheLedger() {
        StatementLine sweep = new StatementLine(2, "S1", START.plusHours(3), "Transfer", "Completed", BigDecimal.ZERO,
                new BigDecimal("90000"), "Utility Account to Organization Settlement Account", null, null, null, Map.of());

        ReconciliationMatcher.Result result = ReconciliationMatcher.match(List.of(sweep), START, END, List.of(),
                List.of(), MARKERS);

        assertThat(result.exceptions()).isEmpty();
        assertThat(result.skippedLines()).isEqualTo(1);
    }

    static StatementLine line(String receipt, String paidIn) {
        return new StatementLine(1, receipt, START.plusHours(1), "Pay Bill Online", "Completed", new BigDecimal(paidIn),
                BigDecimal.ZERO, "Pay Bill Online", "254712***678 - Jane ***", null, null, Map.of());
    }

    static LedgerEntry in(long id, String receipt, String amount, String status, LocalDateTime at) {
        return new LedgerEntry(id, UUID.randomUUID(), LedgerEntry.INCOMING, "IN", "C2B", receipt, new BigDecimal(amount),
                status, null, at, at);
    }

    static LedgerEntry out(long id, String receipt, String amount) {
        return new LedgerEntry(id, UUID.randomUUID(), LedgerEntry.OUTGOING, "OUT", "B2C", receipt,
                new BigDecimal(amount), "SUCCESS", null, START.plusHours(11), START.plusHours(11));
    }

    private static Map<String, Draft> byReceipt(ReconciliationMatcher.Result result) {
        return result.exceptions().stream().collect(Collectors.toMap(Draft::receipt, Function.identity()));
    }
}
