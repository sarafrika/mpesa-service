package com.sarafrika.apps.mpesaservice.services.approvals.bulk;

import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads the bulk payout CSV: a header row naming phone, amount and reference (name optional, any order,
 * any case), then one payout per row. Quoted fields may hold commas, quotes ("") and line breaks. */
public final class BulkPayoutCsv {

    /** One data row with its 1-based line in the file; blank cells are null. */
    public record Row(int line, String phone, String amount, String reference, String name) {
    }

    private static final Map<String, String> HEADERS = Map.of(
            "phone", "phone", "phone_number", "phone", "msisdn", "phone",
            "amount", "amount",
            "reference", "reference", "client_reference", "reference",
            "name", "name", "recipient_name", "name");

    private BulkPayoutCsv() {
    }

    public static List<Row> parse(byte[] content, int maxRows) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<Record> records = records(text);
        if (records.isEmpty()) {
            throw new InvalidRequestException("file", "is empty; it needs a header row and one row per payout");
        }
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = records.get(0).fields();
        for (int i = 0; i < header.size(); i++) {
            String key = HEADERS.get(header.get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
            if (key != null) {
                columns.putIfAbsent(key, i);
            }
        }
        for (String required : List.of("phone", "amount", "reference")) {
            if (!columns.containsKey(required)) {
                throw new InvalidRequestException("file", "needs a header row with phone, amount and reference "
                        + "columns (name is optional); '" + required + "' is missing");
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Record record : records.subList(1, records.size())) {
            if (record.fields().stream().allMatch(String::isBlank)) {
                continue;
            }
            if (rows.size() == maxRows) {
                throw new InvalidRequestException("file", "has more than " + maxRows + " rows; split it into "
                        + "smaller batches");
            }
            rows.add(new Row(record.line(), cell(record, columns.get("phone")), cell(record, columns.get("amount")),
                    cell(record, columns.get("reference")), cell(record, columns.get("name"))));
        }
        if (rows.isEmpty()) {
            throw new InvalidRequestException("file", "has a header but no payout rows");
        }
        return rows;
    }

    private record Record(int line, List<String> fields) {
    }

    private static String cell(Record record, Integer index) {
        if (index == null || index >= record.fields().size()) {
            return null;
        }
        String value = record.fields().get(index).trim();
        return value.isEmpty() ? null : value;
    }

    private static List<Record> records(String text) {
        List<Record> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int start = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                fields.add(field.toString());
                records.add(new Record(start, fields));
                fields = new ArrayList<>();
                field.setLength(0);
                line++;
                start = line;
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !fields.isEmpty()) {
            fields.add(field.toString());
            records.add(new Record(start, fields));
        }
        return records;
    }
}
