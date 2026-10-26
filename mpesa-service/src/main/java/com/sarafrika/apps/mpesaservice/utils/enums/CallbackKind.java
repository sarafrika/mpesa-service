package com.sarafrika.apps.mpesaservice.utils.enums;

import java.util.Locale;

/** Which Safaricom callback a request to /api/v1/callbacks/** is, named after its path (c2b/confirmation is
 * C2B_CONFIRMATION); a path this list does not know is OTHER. */
public enum CallbackKind {
    C2B_VALIDATION, C2B_CONFIRMATION, STK_CALLBACK, B2C_RESULT, B2C_TIMEOUT, B2B_RESULT, B2B_TIMEOUT,
    TRANSACTION_STATUS_RESULT, TRANSACTION_STATUS_TIMEOUT, ACCOUNT_BALANCE_RESULT, ACCOUNT_BALANCE_TIMEOUT,
    REVERSAL_RESULT, REVERSAL_TIMEOUT, OTHER;

    public static final String PREFIX = "/api/v1/callbacks/";

    public static CallbackKind fromPath(String path) {
        if (path == null || !path.startsWith(PREFIX)) {
            return OTHER;
        }
        String name = path.substring(PREFIX.length()).replaceAll("/+$", "")
                .replace('/', '_').replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return OTHER;
        }
    }
}
