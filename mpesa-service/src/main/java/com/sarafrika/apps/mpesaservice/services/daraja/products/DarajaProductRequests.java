package com.sarafrika.apps.mpesaservice.services.daraja.products;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Request bodies of the flagged products, field for field as Safaricom's portal documents them. */
public final class DarajaProductRequests {

    public static final String KRA_SHORTCODE = "572572";
    public static final String PAY_TAX_TO_KRA = "PayTaxToKRA";
    public static final String BUSINESS_PAY_TO_POCHI = "BusinessPayToPochi";

    private DarajaProductRequests() {
    }

    /** developer.safaricom.co.ke/apis/TaxRemittance: identifier types "4" only, PartyB 572572 only and the
     * PRN as AccountReference. The sample writes "Command ID"; its parameter table (used here) says CommandID,
     * and "RecieverIdentifierType" is Safaricom's spelling. */
    public static Map<String, Object> taxRemittance(String initiator, String securityCredential, String partyA,
                                                    BigDecimal amount, String prn, String remarks,
                                                    String queueTimeOutUrl, String resultUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Initiator", initiator);
        body.put("SecurityCredential", securityCredential);
        body.put("CommandID", PAY_TAX_TO_KRA);
        body.put("SenderIdentifierType", "4");
        body.put("RecieverIdentifierType", "4");
        body.put("Amount", whole(amount));
        body.put("PartyA", partyA);
        body.put("PartyB", KRA_SHORTCODE);
        body.put("AccountReference", prn);
        body.put("Remarks", remarks);
        body.put("QueueTimeOutURL", queueTimeOutUrl);
        body.put("ResultURL", resultUrl);
        return body;
    }

    /** developer.safaricom.co.ke/apis/BusinessToPochi: our OriginatorConversationID, PartyB the 12-digit
     * MSISDN, and "Occassion" (Safaricom's spelling, optional). */
    public static Map<String, Object> businessToPochi(String originatorConversationId, String initiatorName,
                                                      String securityCredential, String partyA, String msisdn,
                                                      BigDecimal amount, String remarks, String occasion,
                                                      String queueTimeOutUrl, String resultUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("OriginatorConversationID", originatorConversationId);
        body.put("InitiatorName", initiatorName);
        body.put("SecurityCredential", securityCredential);
        body.put("CommandID", BUSINESS_PAY_TO_POCHI);
        body.put("Amount", whole(amount));
        body.put("PartyA", partyA);
        body.put("PartyB", msisdn);
        body.put("Remarks", remarks);
        body.put("QueueTimeOutURL", queueTimeOutUrl);
        body.put("ResultURL", resultUrl);
        if (occasion != null && !occasion.isBlank()) {
            body.put("Occassion", occasion);
        }
        return body;
    }

    /** developer.safaricom.co.ke/apis/B2BExpressCheckout: primaryShortCode is the paying till, receiverShortCode
     * our paybill; RequestRefID comes back as the callback's requestId. Only the OAuth token authenticates it. */
    public static Map<String, Object> b2bExpressCheckout(String primaryShortCode, String receiverShortCode,
                                                         BigDecimal amount, String paymentRef, String callbackUrl,
                                                         String partnerName, String requestRefId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("primaryShortCode", primaryShortCode);
        body.put("receiverShortCode", receiverShortCode);
        body.put("amount", whole(amount));
        body.put("paymentRef", paymentRef);
        body.put("callbackUrl", callbackUrl);
        body.put("partnerName", partnerName);
        body.put("RequestRefID", requestRefId);
        return body;
    }

    private static String whole(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }
}
