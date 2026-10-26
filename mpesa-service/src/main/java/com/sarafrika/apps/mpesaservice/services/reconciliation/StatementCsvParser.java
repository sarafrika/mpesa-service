package com.sarafrika.apps.mpesaservice.services.reconciliation;

import com.sarafrika.apps.mpesaservice.services.reconciliation.StatementParseException.RowError;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reads the CSV export of an M-Pesa organisation statement: optional preamble rows ("Short Code:", ...), then a
 * header row starting "Receipt No.", then one row per line. Columns are found by name, since exports differ. */
public final class StatementCsvParser {

    private static final java.util.regex.Pattern AMOUNT = java.util.regex.Pattern.compile("^-?\\d{1,13}(\\.\\d{1,2})?$");

    // Header names as exported by the M-Pesa org portal (Receipt No., Completion Time, ... A/C No.); seen in
    // workpointltd/presta-ui docs/resources/c2b-import-template.csv and judeokello/microbima mpesa-payments.service.ts.
    enum Column { RECEIPT, COMPLETION_TIME, INITIATION_TIME, DETAILS, STATUS, PAID_IN, WITHDRAWN, REASON_TYPE,
        OTHER_PARTY, LINKED_TRANSACTION, ACCOUNT }

    private static final Map<String, Column> HEADERS = Map.ofEntries(
            Map.entry("receiptno", Column.RECEIPT), Map.entry("receiptnumber", Column.RECEIPT),
            Map.entry("receipt", Column.RECEIPT),
            Map.entry("completiontime", Column.COMPLETION_TIME), Map.entry("initiationtime", Column.INITIATION_TIME),
            Map.entry("details", Column.DETAILS), Map.entry("transactionstatus", Column.STATUS),
            Map.entry("paidin", Column.PAID_IN), Map.entry("withdrawn", Column.WITHDRAWN),
            Map.entry("withdrawen", Column.WITHDRAWN), Map.entry("reasontype", Column.REASON_TYPE),
            Map.entry("otherpartyinfo", Column.OTHER_PARTY), Map.entry("oppositeparty", Column.OTHER_PARTY),
            Map.entry("linkedtransactionid", Column.LINKED_TRANSACTION), Map.entry("acno", Column.ACCOUNT),
            Map.entry("accountno", Column.ACCOUNT));

    // Completion Time is DD-MM-YYYY HH:mm:ss in the portal export; the others are seen in re-saved files.
    private static final List<DateTimeFormatter> TIMES = List.of(
            DateTimeFormatter.ofPattern("d-M-uuuu H:mm[:ss]"), DateTimeFormatter.ofPattern("uuuu-M-d H:mm[:ss]"),
            DateTimeFormatter.ofPattern("d/M/uuuu H:mm[:ss]"), DateTimeFormatter.ofPattern("uuuu-M-d'T'H:mm[:ss]"),
            DateTimeFormatter.ofPattern("d.M.uuuu H:mm[:ss]"));

    static final int MAX_REPORTED_ERRORS = 50;

    public record ParsedStatement(String shortCode, List<StatementLine> lines) {
    }

    private StatementCsvParser() {
    }

    public static ParsedStatement parse(byte[] content, int maxRows) {
        if (content == null || content.length == 0) {
            throw new StatementParseException("The file is empty");
        }
        if (startsWith(content, 0xD0, 0xCF, 0x11, 0xE0) || startsWith(content, 'P', 'K')) {
            throw new StatementParseException("This is an Excel file; save the statement as CSV and upload that");
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = CsvRows.read(text);

        String shortCode = null;
        int headerIndex = -1;
        for (int i = 0; i < rows.size() && headerIndex < 0; i++) {
            List<String> row = rows.get(i);
            String first = row.isEmpty() ? "" : normalise(row.get(0));
            if (HEADERS.get(first) == Column.RECEIPT) {
                headerIndex = i;
            } else if (first.equals("shortcode") && row.size() > 1) {
                shortCode = row.get(1).trim();
            }
        }
        if (headerIndex < 0) {
            throw new StatementParseException("This is not an M-Pesa organisation statement: no header row starting "
                    + "with \"Receipt No.\" was found");
        }
        Map<Column, Integer> columns = columns(rows.get(headerIndex));
        List<String> missing = new ArrayList<>();
        if (!columns.containsKey(Column.COMPLETION_TIME) && !columns.containsKey(Column.INITIATION_TIME)) {
            missing.add("Completion Time");
        }
        if (!columns.containsKey(Column.STATUS)) {
            missing.add("Transaction Status");
        }
        if (!columns.containsKey(Column.PAID_IN)) {
            missing.add("Paid In");
        }
        if (!columns.containsKey(Column.WITHDRAWN)) {
            missing.add("Withdrawn");
        }
        if (!missing.isEmpty()) {
            throw new StatementParseException("This statement has no " + String.join(", ", missing)
                    + " column; export the full statement from the M-Pesa org portal");
        }

        List<String> header = rows.get(headerIndex);
        List<StatementLine> lines = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int errorCount = 0;
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int lineNumber = i + 1;
            String receipt = cell(row, columns, Column.RECEIPT);
            if (receipt.isEmpty()) {
                continue; // blank lines and trailing totals
            }
            if (lines.size() >= maxRows) {
                throw new StatementParseException("The statement has more than " + maxRows
                        + " lines; upload a shorter period");
            }
            try {
                lines.add(line(lineNumber, receipt, row, header, columns));
            } catch (IllegalArgumentException e) {
                errorCount++;
                if (errors.size() < MAX_REPORTED_ERRORS) {
                    errors.add(new RowError(lineNumber, e.getMessage()));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new StatementParseException(errorCount + (errorCount == 1 ? " line" : " lines")
                    + " could not be read; nothing was reconciled", errors);
        }
        return new ParsedStatement(shortCode, lines);
    }

    private static StatementLine line(int lineNumber, String receipt, List<String> row, List<String> header,
                                      Map<Column, Integer> columns) {
        String time = cell(row, columns, Column.COMPLETION_TIME);
        if (time.isEmpty()) {
            time = cell(row, columns, Column.INITIATION_TIME);
        }
        Map<String, String> raw = new LinkedHashMap<>();
        for (int c = 0; c < header.size() && c < row.size(); c++) {
            if (!header.get(c).isBlank()) {
                raw.put(header.get(c).trim(), row.get(c).trim());
            }
        }
        return new StatementLine(lineNumber, receipt.toUpperCase(Locale.ROOT), time(time),
                blankToNull(cell(row, columns, Column.DETAILS)), blankToNull(cell(row, columns, Column.STATUS)),
                amount("Paid In", cell(row, columns, Column.PAID_IN), false),
                amount("Withdrawn", cell(row, columns, Column.WITHDRAWN), true),
                blankToNull(cell(row, columns, Column.REASON_TYPE)), blankToNull(cell(row, columns, Column.OTHER_PARTY)),
                Optional.ofNullable(blankToNull(cell(row, columns, Column.LINKED_TRANSACTION)))
                        .map(v -> v.toUpperCase(Locale.ROOT)).orElse(null),
                blankToNull(cell(row, columns, Column.ACCOUNT)), raw);
    }

    static LocalDateTime time(String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Completion Time is empty");
        }
        for (DateTimeFormatter format : TIMES) {
            try {
                return LocalDateTime.parse(value.trim(), format);
            } catch (DateTimeParseException ignored) {
                // try the next layout
            }
        }
        throw new IllegalArgumentException("Completion Time '" + value + "' is not a date and time");
    }

    /** "1,131,340.00", "-60.00", "(60.00)" and "" (zero) are read as plain shillings and cents, the sign dropped;
     * anything else (exponents, more than two decimals, text) is a row error, as is money in shown negative. */
    static BigDecimal amount(String column, String value, boolean negativeAllowed) {
        String trimmed = value.trim();
        boolean bracketed = trimmed.startsWith("(") && trimmed.endsWith(")");
        String cleaned = (bracketed ? trimmed.substring(1, trimmed.length() - 1) : trimmed).replace(",", "")
                .replace("KES", "").replace("Ksh", "").replace(" ", "");
        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return BigDecimal.ZERO;
        }
        if (!AMOUNT.matcher(cleaned).matches() || (bracketed && cleaned.startsWith("-"))) {
            throw new IllegalArgumentException(column + " '" + value + "' is not an amount");
        }
        BigDecimal amount = new BigDecimal(cleaned);
        if (!negativeAllowed && (bracketed || amount.signum() < 0)) {
            throw new IllegalArgumentException(column + " '" + value + "' is negative");
        }
        return amount.abs();
    }

    private static Map<Column, Integer> columns(List<String> header) {
        Map<Column, Integer> columns = new EnumMap<>(Column.class);
        for (int i = 0; i < header.size(); i++) {
            Column column = HEADERS.get(normalise(header.get(i)));
            if (column != null) {
                columns.putIfAbsent(column, i);
            }
        }
        return columns;
    }

    private static String cell(List<String> row, Map<Column, Integer> columns, Column column) {
        Integer index = columns.get(column);
        return index == null || index >= row.size() ? "" : row.get(index).trim();
    }

    private static String normalise(String header) {
        return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean startsWith(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((content[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** RFC 4180 rows: quoted fields may hold commas, doubled quotes and line breaks; CRLF or LF ends a row. */
    static final class CsvRows {
        private CsvRows() {
        }

        static List<List<String>> read(String text) {
            List<List<String>> rows = new ArrayList<>();
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quoted) {
                    if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else if (c == '"') {
                        quoted = false;
                    } else {
                        field.append(c);
                    }
                } else if (c == '"') {
                    quoted = true;
                } else if (c == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n' || c == '\r') {
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                } else {
                    field.append(c);
                }
            }
            if (field.length() > 0 || !row.isEmpty()) {
                row.add(field.toString());
                rows.add(row);
            }
            return rows;
        }
    }
}
