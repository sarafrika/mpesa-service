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

    @Test
    void phonesAndNamesAreMaskedAndShortened() {
        assertThat(PersonalData.maskPhone("254712345123")).isEqualTo("2547•••••123");
        assertThat(PersonalData.maskPhone("12345")).isEqualTo("•••••");
        assertThat(PersonalData.shortName("Jane Wanjiru Kamau")).isEqualTo("J. Kamau");
        assertThat(PersonalData.shortName("jane")).isEqualTo("J.");
        assertThat(PersonalData.maskPhone(null)).isNull();
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
