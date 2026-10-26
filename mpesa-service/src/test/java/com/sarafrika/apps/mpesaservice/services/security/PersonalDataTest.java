package com.sarafrika.apps.mpesaservice.services.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Who sees customers' phone numbers and names in full, and how everyone else sees them. */
class PersonalDataTest {

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"mpesa-viewer,false", "mpesa-operator,false", "mpesa-finance,true", "mpesa-admin,true"})
    void onlyFinanceAndAdminSeeFullDetails(String role, boolean visible) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("jane", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        assertThat(PersonalData.visible()).isEqualTo(visible);
    }

    @Test
    void nobodySignedInSeesMaskedDetails() {
        assertThat(PersonalData.visible()).isFalse();
    }

    // Daraja's reversal result (developer.safaricom.co.ke/apis/Reversal) and a Transaction Status result.
    static final String REVERSAL_RESULT = """
            {"Result":{"ResultType":0,"ResultCode":0,"ResultDesc":"The service request is processed successfully.",
              "OriginatorConversationID":"8521-4298025-1","ConversationID":"AG_20181005_00004d7ee675c0c7ee0b",
              "TransactionID":"MJ561H6X5O","ResultParameters":{"ResultParameter":[
                {"Key":"Amount","Value":"100"},{"Key":"OriginalTransactionID","Value":"MIP3XXXX"},
                {"Key":"CreditPartyPublicName","Value":"254708374149 - John Doe"},
                {"Key":"DebitPartyPublicName","Value":"601315 - Safaricom1338"}]}}}""";
    static final String STATUS_RESULT = """
            {"Result":{"ResultCode":0,"ConversationID":"AG_20180223_0000493344ae97d86f75","ResultParameters":{
              "ResultParameter":[{"Key":"DebitPartyName","Value":"600310 - Safaricom333"},
                {"Key":"CreditPartyName","Value":"254722000000 - Jane Wanjiru Doe"},
                {"Key":"ReceiptNo","Value":"MBN0000000"}]}}}""";

    @ParameterizedTest
    @CsvSource({"mpesa-viewer,false", "mpesa-operator,false", "mpesa-finance,true", "mpesa-admin,true"})
    void partyNamesInReversalAndStatusResultsAreMaskedByRole(String role, boolean full) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("jane", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        JsonNode reversal = json.readTree(REVERSAL_RESULT);
        JsonNode status = json.readTree(STATUS_RESULT);
        String shownReversal = (PersonalData.visible() ? reversal : PersonalData.redact(reversal)).toString();
        String shownStatus = (PersonalData.visible() ? status : PersonalData.redact(status)).toString();

        if (full) {
            assertThat(shownReversal).contains("254708374149 - John Doe");
            assertThat(shownStatus).contains("254722000000 - Jane Wanjiru Doe");
        } else {
            assertThat(shownReversal).doesNotContain("254708374149").doesNotContain("John")
                    .contains("2547•••••149 - J. Doe").contains("601315 - S.").contains("MIP3XXXX");
            assertThat(shownStatus).doesNotContain("254722000000").doesNotContain("Wanjiru")
                    .contains("2547•••••000 - J. Doe").contains("MBN0000000");
        }
    }

    @Test
    void phonesAndNamesAreMaskedAndShortened() {
        assertThat(PersonalData.maskPhone("254712345123")).isEqualTo("2547•••••123");
        assertThat(PersonalData.maskPhone("12345")).isEqualTo("•••••");
        assertThat(PersonalData.shortName("Jane Wanjiru Kamau")).isEqualTo("J. Kamau");
        assertThat(PersonalData.shortName("jane")).isEqualTo("J.");
        assertThat(PersonalData.maskPhone(null)).isNull();
    }

    @Test
    void statementTextAndLinesKeptInRawDataAreMasked() throws Exception {
        assertThat(PersonalData.maskText("Customer Transfer to 254705912645 - NICHOLAS JOHN SONGOK Acc. INV-9"))
                .isEqualTo("Customer Transfer to 2547•••••645 - N. SONGOK Acc. INV-9");
        assertThat(PersonalData.maskText("Pay Bill from 254722***111 - John *** Acc. ELM-1"))
                .isEqualTo("Pay Bill from 2547•••••111 - J. *** Acc. ELM-1");
        assertThat(PersonalData.maskText("0722000111")).isEqualTo("0722•••••111");
        assertThat(PersonalData.maskText("ELM-003019")).isEqualTo("ELM-003019");
        assertThat(PersonalData.maskPublicName("601315 - Safaricom1338")).isEqualTo("601315 - S.");
        JsonNode line = PersonalData.redact(json.readTree("""
                {"StatementLine":{"Other Party Info":"254722123111 - JOHN KAMAU",
                "Details":"Pay Bill from 254722123111 - JOHN KAMAU Acc. ELM-1","Receipt No.":"TJP9X2K0BB"}}"""));
        assertThat(line.at("/StatementLine/Other Party Info").asText()).isEqualTo("2547•••••111 - J. KAMAU");
        assertThat(line.at("/StatementLine/Details").asText()).isEqualTo("Pay Bill from 2547•••••111 - J. KAMAU Acc. ELM-1");
        assertThat(line.at("/StatementLine/Receipt No.").asText()).isEqualTo("TJP9X2K0BB");
    }

    @Test
    void rawCallbacksAndPayloadsAreRedactedAtAnyDepth() throws Exception {
        JsonNode stk = json.readTree("""
                {"Body":{"stkCallback":{"CallbackMetadata":{"Item":[
                  {"Name":"Amount","Value":100},{"Name":"PhoneNumber","Value":254712345123}]}}}}""");
        JsonNode c2b = json.readTree("""
                {"MSISDN":"254712345123","FirstName":"JANE","MiddleName":"W","LastName":"KAMAU","TransID":"TX1"}""");
        JsonNode b2c = json.readTree("""
                {"Result":{"ResultParameters":{"ResultParameter":[
                  {"Key":"ReceiverPartyPublicName","Value":"254712345123 - JANE WANJIRU KAMAU"}]}}}""");
        JsonNode webhook = json.readTree("""
                {"data":{"msisdn":"254712345123","first_name":"Jane","recipient_name":"Jane Kamau","amount":5}}""");

        assertThat(PersonalData.redact(stk).toString()).contains("\"Value\":\"2547•••••123\"")
                .doesNotContain("254712345123").contains("\"Value\":100");
        assertThat(PersonalData.redact(c2b).toString()).contains("\"MSISDN\":\"2547•••••123\"")
                .contains("\"FirstName\":\"J.\"").contains("\"MiddleName\":\"\"").contains("TX1");
        assertThat(PersonalData.redact(b2c).toString()).contains("2547•••••123 - J. KAMAU")
                .doesNotContain("WANJIRU");
        assertThat(PersonalData.redact(webhook).toString()).contains("\"recipient_name\":\"J. Kamau\"")
                .doesNotContain("254712345123");
        assertThat(c2b.get("MSISDN").asText()).isEqualTo("254712345123");
    }
}
