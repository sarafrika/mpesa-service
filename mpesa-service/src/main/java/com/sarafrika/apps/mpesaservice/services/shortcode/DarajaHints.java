package com.sarafrika.apps.mpesaservice.services.shortcode;

/** What to tell a person about a known Daraja failure. */
public final class DarajaHints {

    /** B2C ResultCode 2001, "The initiator information is invalid." (Daraja B2C result codes,
     * developer.safaricom.co.ke/APIs/BusinessToCustomer). For STK the same code means a wrong PIN. */
    public static final int INVALID_INITIATOR = 2001;

    public static final String INVALID_INITIATOR_HINT = "The initiator name, password or Safaricom certificate "
            + "is wrong or the password was reset at Safaricom. Update the credentials.";

    private DarajaHints() {
    }

    /** The hint for a payout's result code, or null. */
    public static String forPayout(Integer resultCode) {
        return resultCode != null && resultCode == INVALID_INITIATOR ? INVALID_INITIATOR_HINT : null;
    }
}
