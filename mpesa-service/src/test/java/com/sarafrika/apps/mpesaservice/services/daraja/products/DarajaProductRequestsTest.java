package com.sarafrika.apps.mpesaservice.services.daraja.products;

import static org.assertj.core.api.Assertions.assertThat;

import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Each body is checked against the request sample on its developer.safaricom.co.ke/apis page. */
class DarajaProductRequestsTest {

    @Test
    void taxRemittanceMatchesThePortalSample() {
        Map<String, Object> body = DarajaProductRequests.taxRemittance("TaxPayer", "FKXl==", "888880",
                new BigDecimal("239.00"), "353353", "OK", "https://mydomain.com/b2b/remittax/queue/",
                "https://mydomain.com/b2b/remittax/result/");

        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("Initiator", "TaxPayer");
        sample.put("SecurityCredential", "FKXl==");
        sample.put("CommandID", "PayTaxToKRA");
        sample.put("SenderIdentifierType", "4");
        sample.put("RecieverIdentifierType", "4");
        sample.put("Amount", "239");
        sample.put("PartyA", "888880");
        sample.put("PartyB", "572572");
        sample.put("AccountReference", "353353");
        sample.put("Remarks", "OK");
        sample.put("QueueTimeOutURL", "https://mydomain.com/b2b/remittax/queue/");
        sample.put("ResultURL", "https://mydomain.com/b2b/remittax/result/");
        assertThat(body).containsExactlyEntriesOf(sample);
    }

    @Test
    void businessToPochiMatchesThePortalSample() {
        Map<String, Object> body = DarajaProductRequests.businessToPochi("600997_Test_32et3241ed8yu", "testapi",
                "RC6E9WDxXR4b9X2c6z3gp0oC5Th ==", "600992", "254705912645", new BigDecimal("10"), "remarked",
                "ChristmasPay", "https://mydomain.com/path", "https://mydomain.com/path");

        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("OriginatorConversationID", "600997_Test_32et3241ed8yu");
        sample.put("InitiatorName", "testapi");
        sample.put("SecurityCredential", "RC6E9WDxXR4b9X2c6z3gp0oC5Th ==");
        sample.put("CommandID", "BusinessPayToPochi");
        sample.put("Amount", "10");
        sample.put("PartyA", "600992");
        sample.put("PartyB", "254705912645");
        sample.put("Remarks", "remarked");
        sample.put("QueueTimeOutURL", "https://mydomain.com/path");
        sample.put("ResultURL", "https://mydomain.com/path");
        sample.put("Occassion", "ChristmasPay");
        assertThat(body).containsExactlyEntriesOf(sample);
    }

    @Test
    void pochiLeavesOutTheOptionalOccasion() {
        Map<String, Object> body = DarajaProductRequests.businessToPochi("id", "api", "cred", "600992",
                "254705912645", new BigDecimal("10"), "remarked", " ", "t", "r");
        assertThat(body).doesNotContainKey("Occassion");
    }

    @Test
    void b2bExpressCheckoutMatchesThePortalSample() {
        Map<String, Object> body = DarajaProductRequests.b2bExpressCheckout("000001", "000002", new BigDecimal("100"),
                "paymentRef", "http://example.test/result", "Vendor", "550e8400-e29b-41d4-a716-446655440000");

        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("primaryShortCode", "000001");
        sample.put("receiverShortCode", "000002");
        sample.put("amount", "100");
        sample.put("paymentRef", "paymentRef");
        sample.put("callbackUrl", "http://example.test/result");
        sample.put("partnerName", "Vendor");
        sample.put("RequestRefID", "550e8400-e29b-41d4-a716-446655440000");
        assertThat(body).containsExactlyEntriesOf(sample);
    }

    @Test
    void sandboxUrlsAreThePortalOnesAndProductionPathsAreSettings() {
        DarajaProductEndpoints endpoints = new DarajaProductEndpoints("https://sandbox.safaricom.co.ke/",
                "https://api.safaricom.co.ke", "https://pay.example.test/api/v1/callbacks/", "/custom/remittax",
                "/custom/ussdpush");

        assertThat(endpoints.requestUrl(DarajaFeature.TAX_REMITTANCE, Environment.SANDBOX))
                .hasToString("https://sandbox.safaricom.co.ke/mpesa/b2b/v1/remittax");
        assertThat(endpoints.requestUrl(DarajaFeature.BUSINESS_TO_POCHI, Environment.SANDBOX))
                .hasToString("https://sandbox.safaricom.co.ke/mpesa/b2pochi/v1/paymentrequest");
        assertThat(endpoints.requestUrl(DarajaFeature.B2B_EXPRESS_CHECKOUT, Environment.SANDBOX))
                .hasToString("https://sandbox.safaricom.co.ke/v1/ussdpush/get-msisdn");
        assertThat(endpoints.requestUrl(DarajaFeature.BUSINESS_TO_POCHI, Environment.PRODUCTION))
                .hasToString("https://api.safaricom.co.ke/mpesa/b2pochi/v1/paymentrequest");
        assertThat(endpoints.requestUrl(DarajaFeature.TAX_REMITTANCE, Environment.PRODUCTION))
                .hasToString("https://api.safaricom.co.ke/custom/remittax");
        assertThat(endpoints.requestUrl(DarajaFeature.B2B_EXPRESS_CHECKOUT, Environment.PRODUCTION))
                .hasToString("https://api.safaricom.co.ke/custom/ussdpush");
        assertThat(endpoints.callbackUrl("/pochi/result"))
                .isEqualTo("https://pay.example.test/api/v1/callbacks/pochi/result");
    }
}
