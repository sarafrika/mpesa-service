package com.sarafrika.apps.mpesaservice.services.approvals.bulk;

import java.util.Optional;
import java.util.regex.Pattern;

/** Turns the ways people write a Kenyan mobile number (0712 345 678, 712345678, +254712345678, 0110...)
 * into the 2547XXXXXXXX / 2541XXXXXXXX form B2C payouts take. */
public final class KenyanPhones {

    private static final Pattern MSISDN = Pattern.compile("254[17]\\d{8}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");

    private KenyanPhones() {
    }

    public static Optional<String> normalise(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.length() == 10 && digits.startsWith("0")) {
            digits = "254" + digits.substring(1);
        } else if (digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("1"))) {
            digits = "254" + digits;
        }
        return MSISDN.matcher(digits).matches() ? Optional.of(digits) : Optional.empty();
    }
}
