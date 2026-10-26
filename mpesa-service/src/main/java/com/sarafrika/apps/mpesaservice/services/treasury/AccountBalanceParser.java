package com.sarafrika.apps.mpesaservice.services.treasury;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Daraja Account Balance docs (developer.safaricom.co.ke/apis/AccountBalance): accounts joined by "&", each
// "<Account Type Alias>|<Currency>|<Current Balance>|<Available Balance>|<Reserved Balance>|<Unclear Balance>";
// the B2B result sample (Working Account|KES|346568.83|6186.83|340382.00|0.00) confirms current = available + reserved.
public final class AccountBalanceParser {

    public static final String UTILITY = "Utility Account";
    public static final String WORKING = "Working Account";
    public static final String CHARGES_PAID = "Charges Paid Account";

    private AccountBalanceParser() {
    }

    /** One account as Safaricom listed it; amounts are null when a column is missing. */
    public record Account(String name, String currency, BigDecimal available, BigDecimal current,
                          BigDecimal reserved, BigDecimal uncleared) {
    }

    /** Every account Safaricom listed, in its order; malformed entries are skipped, never guessed. */
    public static List<Account> parse(String value) {
        List<Account> accounts = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return accounts;
        }
        for (String entry : value.split("&")) {
            String[] parts = entry.split("\\|", -1);
            if (parts.length < 3 || parts[0].isBlank()) {
                continue;
            }
            accounts.add(new Account(parts[0].trim(), parts[1].trim(), amount(parts, 3), amount(parts, 2),
                    amount(parts, 4), amount(parts, 5)));
        }
        return accounts;
    }

    /** The account with this name (any case), e.g. {@link #UTILITY}. */
    public static Optional<Account> find(List<Account> accounts, String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return accounts.stream().filter(a -> a.name().toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }

    private static BigDecimal amount(String[] parts, int index) {
        if (index >= parts.length || parts[index].isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(parts[index].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
