package com.sarafrika.apps.mpesaservice.services.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fixtures follow the M-Pesa org portal export: the preamble rows and header line of
 * github.com/workpointltd/presta-ui docs/resources/c2b-import-template.csv (columns as in judeokello/microbima's
 * parser), and the G2 variant with Opposite Party and Currency from gitmupanzi/controle_interne data-contracts.md. */
class StatementCsvParserTest {

    @Test
    void readsThePortalExportWithItsPreambleBomQuotesAndSigns() throws IOException {
        StatementCsvParser.ParsedStatement parsed = StatementCsvParser.parse(fixture("org-statement-utility.csv"), 1000);

        assertThat(parsed.shortCode()).isEqualTo("600100");
        List<StatementLine> lines = parsed.lines();
        assertThat(lines).hasSize(7); // the blank trailing row is skipped
        StatementLine first = lines.getFirst();
        assertThat(first.receipt()).isEqualTo("TJP9X2K1AA");
        assertThat(first.completedAt()).isEqualTo(LocalDateTime.of(2026, 10, 25, 9, 15, 2));
        assertThat(first.paidIn()).isEqualByComparingTo("5000.00");
        assertThat(first.accountNumber()).isEqualTo("ELM-002211");
        assertThat(first.otherParty()).isEqualTo("254712***678 - Jane ***");
        assertThat(first.raw()).containsEntry("Reason Type", "Pay Bill Online");

        assertThat(lines.get(1).paidIn()).isEqualByComparingTo("2500.00"); // "2,500.00"
        StatementLine payout = lines.get(2);
        assertThat(payout.withdrawn()).isEqualByComparingTo("3000.00"); // -3000.00 read without its sign
        assertThat(payout.moneyIn()).isFalse();
        assertThat(payout.details()).isEqualTo("Business Payment to 254733***222 - Mary ***, \"refund\"");
        assertThat(lines.get(4).details()).isEqualTo("Pay Bill Online\nsecond line");
        assertThat(lines.get(4).completed()).isFalse();
        assertThat(lines.get(6).linkedTransactionId()).isEqualTo("TJP9X2K4EE");
    }

    @Test
    void findsColumnsByNameInTheG2Variant() throws IOException {
        StatementCsvParser.ParsedStatement parsed = StatementCsvParser.parse(fixture("org-statement-g2-variant.csv"), 10);

        StatementLine line = parsed.lines().getFirst();
        assertThat(parsed.shortCode()).isNull();
        assertThat(line.completedAt()).isEqualTo(LocalDateTime.of(2026, 10, 25, 8, 0));
        assertThat(line.otherParty()).isEqualTo("254712***678 - Jane ***");
        assertThat(line.accountNumber()).isNull();
        assertThat(StatementBackfill.accountReference(line)).isEqualTo("INV-7");
    }

    @Test
    void refusesFilesThatAreNotStatements() throws IOException {
        assertThatThrownBy(() -> StatementCsvParser.parse(fixture("not-a-statement.csv"), 10))
                .isInstanceOf(StatementParseException.class).hasMessageContaining("Receipt No.");
        assertThatThrownBy(() -> StatementCsvParser.parse(fixture("statement-missing-columns.csv"), 10))
                .isInstanceOf(StatementParseException.class).hasMessageContaining("Paid In, Withdrawn");
        assertThatThrownBy(() -> StatementCsvParser.parse(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}, 10))
                .isInstanceOf(StatementParseException.class).hasMessageContaining("Excel");
        assertThatThrownBy(() -> StatementCsvParser.parse(new byte[0], 10))
                .isInstanceOf(StatementParseException.class).hasMessageContaining("empty");
    }

    @Test
    void namesEveryLineThatCouldNotBeRead() throws IOException {
        assertThatThrownBy(() -> StatementCsvParser.parse(fixture("statement-bad-rows.csv"), 10))
                .isInstanceOfSatisfying(StatementParseException.class, e -> {
                    assertThat(e.getMessage()).startsWith("2 lines could not be read");
                    assertThat(e.rowErrors()).extracting(StatementParseException.RowError::line).containsExactly(3, 4);
                    assertThat(e.rowErrors().get(0).message()).contains("yesterday");
                    assertThat(e.rowErrors().get(1).message()).contains("Paid In 'abc'");
                });
    }

    @Test
    void enforcesTheRowLimit() throws IOException {
        assertThatThrownBy(() -> StatementCsvParser.parse(fixture("org-statement-utility.csv"), 3))
                .isInstanceOf(StatementParseException.class).hasMessageContaining("more than 3 lines");
    }

    @Test
    void readsAmountsAndTimesInTheLayoutsSeenInExports() {
        assertThat(StatementCsvParser.amount("Paid In", "\"1,131,340.00\"".replace("\"", "")))
                .isEqualByComparingTo(new BigDecimal("1131340.00"));
        assertThat(StatementCsvParser.amount("Withdrawn", "(60.00)")).isEqualByComparingTo("60");
        assertThat(StatementCsvParser.amount("Withdrawn", "")).isEqualByComparingTo("0");
        assertThat(StatementCsvParser.time("07-04-2025 23:01:41")).isEqualTo(LocalDateTime.of(2025, 4, 7, 23, 1, 41));
        assertThat(StatementCsvParser.time("2025-04-07 23:01:41")).isEqualTo(LocalDateTime.of(2025, 4, 7, 23, 1, 41));
        assertThat(StatementCsvParser.time("7/4/2025 23:01")).isEqualTo(LocalDateTime.of(2025, 4, 7, 23, 1));
    }

    static byte[] fixture(String name) throws IOException {
        try (InputStream in = StatementCsvParserTest.class.getResourceAsStream("/reconciliation/" + name)) {
            return in.readAllBytes();
        }
    }
}
