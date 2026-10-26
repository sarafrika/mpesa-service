package com.sarafrika.apps.mpesaservice.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void cellsThatCouldStartAFormulaAreDefused() {
        assertThat(Csv.cell("=SUM(A1:A2)")).isEqualTo("'=SUM(A1:A2)");
        assertThat(Csv.cell("+254712")).isEqualTo("'+254712");
        assertThat(Csv.cell("-1")).isEqualTo("'-1");
        assertThat(Csv.cell("@cmd")).isEqualTo("'@cmd");
        assertThat(Csv.cell("\tx")).isEqualTo("'\tx");
        assertThat(Csv.cell("ELM-004512")).isEqualTo("ELM-004512");
    }

    @Test
    void separatorsQuotesAndNewlinesAreQuoted() throws Exception {
        StringWriter out = new StringWriter();
        Csv.row(out, Arrays.asList("a,b", "say \"hi\"", null, "two\nlines", 4500));
        assertThat(out).hasToString("\"a,b\",\"say \"\"hi\"\"\",,\"two\nlines\",4500\r\n");
    }
}
