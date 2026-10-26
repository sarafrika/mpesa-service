package com.sarafrika.apps.mpesaservice.utils;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** RFC 4180 CSV cells, safe to open in a spreadsheet: a cell that could start a formula gets a leading '. */
public final class Csv {

    private Csv() {
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    public static void row(Writer out, List<?> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(cell(cells.get(i)));
        }
        out.write("\r\n");
    }
}
