package com.sarafrika.apps.mpesaservice.services.collections;

import java.util.Set;

/** Result codes a C2B validation response may carry (Daraja C2B Register URL docs, "Validation response",
 * developer.safaricom.co.ke/APIs/CustomerToBusinessRegisterURL). "0" accepts; any of the others rejects. */
public final class C2BResultCodes {

    public static final String ACCEPTED = "0";
    public static final String INVALID_MSISDN = "C2B00011";
    public static final String INVALID_ACCOUNT = "C2B00012";
    public static final String INVALID_AMOUNT = "C2B00013";
    public static final String INVALID_KYC = "C2B00014";
    public static final String INVALID_SHORTCODE = "C2B00015";
    public static final String OTHER_ERROR = "C2B00016";

    public static final Set<String> REJECTIONS = Set.of(INVALID_MSISDN, INVALID_ACCOUNT, INVALID_AMOUNT,
            INVALID_KYC, INVALID_SHORTCODE, OTHER_ERROR);

    private C2BResultCodes() {
    }
}
