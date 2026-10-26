package com.sarafrika.apps.mpesaservice.services.approvals.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BulkPayoutCsvTest {

    @ParameterizedTest
    @CsvSource({"0712345678,254712345678", "712345678,254712345678", "+254712345678,254712345678",
            "254712345678,254712345678", "0712 345 678,254712345678", "0110-123-456,254110123456",
            "110123456,254110123456"})
    void kenyanMobileNumbersAreNormalised(String raw, String expected) {
        assertThat(KenyanPhones.normalise(raw)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "0212345678", "25471234567", "2547123456789", "+1 202 555 0100", "abc"})
    void otherNumbersAreRefused(String raw) {
        assertThat(KenyanPhones.normalise(raw)).isEmpty();
    }

    @Test
    void readsColumnsByHeaderInAnyOrderWithQuotesAndCrlf() {
        String csv = "﻿Reference,Name,AMOUNT,Phone\r\n"
                + "OCT-1,\"Kamau, Jane \"\"JK\"\"\",\"1,500\",0712345678\r\n"
                + ",,,\r\n"
                + "OCT-2,,200,0712345679";

        List<BulkPayoutCsv.Row> rows = BulkPayoutCsv.parse(csv.getBytes(StandardCharsets.UTF_8), 10);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(new BulkPayoutCsv.Row(2, "0712345678", "1,500", "OCT-1", "Kamau, Jane \"JK\""));
        assertThat(rows.get(1).line()).isEqualTo(4);
        assertThat(rows.get(1).name()).isNull();
    }

    @Test
    void needsAHeaderAndRows() {
        assertThatThrownBy(() -> BulkPayoutCsv.parse("phone,amount\n0712345678,1\n".getBytes(StandardCharsets.UTF_8), 10))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("reference");
        assertThatThrownBy(() -> BulkPayoutCsv.parse("phone,amount,reference\n".getBytes(StandardCharsets.UTF_8), 10))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("no payout rows");
        assertThatThrownBy(() -> BulkPayoutCsv.parse(new byte[0], 10)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> BulkPayoutCsv.parse("phone,amount,reference\n1,1,a\n2,2,b\n"
                .getBytes(StandardCharsets.UTF_8), 1)).hasMessageContaining("more than 1 rows");
    }
}
