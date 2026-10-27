package com.sarafrika.apps.mpesaservice.services.shortcode;

import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.ACCOUNT_BALANCE;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.B2B_EXPRESS_CHECKOUT;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.B2C_PAYOUT;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.C2B_REGISTER;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.C2B_VALIDATION;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.POCHI;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.PULL_TRANSACTIONS;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.QR;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.REVERSAL;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.STK_PUSH;
import static com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability.TRANSACTION_STATUS;

import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/** The one table of what each shortcode type can do (SHORTCODE-TYPES.md, matrix 1a). Entry points check it
 * before calling Daraja; the console reads it from a shortcode's capabilities, never from its own copy. */
public final class ShortcodeCapabilities {

    private static final Map<ShortcodeType, Set<ShortcodeCapability>> TABLE = new EnumMap<>(ShortcodeType.class);

    static {
        TABLE.put(ShortcodeType.PAYBILL, EnumSet.of(C2B_REGISTER, C2B_VALIDATION, STK_PUSH, QR, PULL_TRANSACTIONS,
                REVERSAL, TRANSACTION_STATUS, ACCOUNT_BALANCE, B2B_EXPRESS_CHECKOUT));
        // A till pays as the payer in B2B Express Checkout, never as the receiver (B2BExpressCheckout doc).
        TABLE.put(ShortcodeType.TILL, EnumSet.of(C2B_REGISTER, C2B_VALIDATION, STK_PUSH, QR, PULL_TRANSACTIONS,
                REVERSAL, TRANSACTION_STATUS, ACCOUNT_BALANCE));
        // B2C reversals are manual on the portal (Reversal FAQ); STK/C2B on a B2C shortcode is undocumented.
        TABLE.put(ShortcodeType.B2C, EnumSet.of(B2C_PAYOUT, POCHI, TRANSACTION_STATUS, ACCOUNT_BALANCE));
        TABLE.put(ShortcodeType.ONE_ACCOUNT, EnumSet.allOf(ShortcodeCapability.class));
    }

    private ShortcodeCapabilities() {
    }

    /** The column is NOT NULL; an unsaved shortcode without a type is treated as a paybill, as before types. */
    public static Set<ShortcodeCapability> of(ShortcodeType type) {
        return Collections.unmodifiableSet(TABLE.get(type == null ? ShortcodeType.PAYBILL : type));
    }

    /** In declaration order, as the API returns them. */
    public static List<ShortcodeCapability> list(ShortcodeType type) {
        return List.copyOf(EnumSet.copyOf(of(type)));
    }

    public static boolean has(MpesaShortCode code, ShortcodeCapability capability) {
        return code != null && of(code.getShortcodeType()).contains(capability);
    }

    /** Throws a 409 naming the shortcode's type when it cannot do this. */
    public static void require(MpesaShortCode code, ShortcodeCapability capability) {
        if (!has(code, capability)) {
            throw new CapabilityNotSupportedException(capability, refusal(code, capability));
        }
    }

    /** Null when the shortcode can do this, otherwise the sentence the console shows. */
    public static String refusal(MpesaShortCode code, ShortcodeCapability capability) {
        if (has(code, capability)) {
            return null;
        }
        String number = code == null || code.getShortcode() == null ? "This shortcode"
                : "Shortcode " + code.getShortcode();
        ShortcodeType type = code == null ? null : code.getShortcodeType();
        String hint = type == ShortcodeType.B2C && capability == REVERSAL
                ? " Safaricom does B2C reversals by hand on the M-PESA org portal." : "";
        return number + " is a " + label(type) + ", so it cannot do " + label(capability) + "." + hint;
    }

    /** The capability a flagged Daraja product needs, or null when no type rule applies (Tax, Ratiba). */
    public static ShortcodeCapability forFeature(DarajaFeature feature) {
        return switch (feature) {
            case PULL_TRANSACTIONS -> PULL_TRANSACTIONS;
            case BUSINESS_TO_POCHI -> POCHI;
            case B2B_EXPRESS_CHECKOUT -> B2B_EXPRESS_CHECKOUT;
            case TAX_REMITTANCE, RATIBA -> null;
        };
    }

    public static boolean allows(MpesaShortCode code, DarajaFeature feature) {
        ShortcodeCapability needed = forFeature(feature);
        return needed == null || has(code, needed);
    }

    /** The Go-Live number: a till's store/HO number (STK FAQ 10), otherwise the shortcode itself.
     * Which number C2B register, status, balance and reversal take for a till is unconfirmed,
     * see SHORTCODE-TYPES.md items 1-3; the Go-Live shortcode is the documented anchor. */
    public static String goLiveNumber(MpesaShortCode code) {
        if (code.getShortcodeType() == ShortcodeType.TILL && StringUtils.hasText(code.getStoreNumber())) {
            return code.getStoreNumber().trim();
        }
        return code.getShortcode();
    }

    public static String label(ShortcodeType type) {
        if (type == null) {
            return "shortcode";
        }
        return switch (type) {
            case PAYBILL -> "paybill";
            case TILL -> "till (Buy Goods)";
            case B2C -> "B2C disbursement shortcode";
            case ONE_ACCOUNT -> "One Account (paybill and B2C)";
        };
    }

    static String label(ShortcodeCapability capability) {
        return switch (capability) {
            case C2B_REGISTER -> "C2B URL registration";
            case C2B_VALIDATION -> "C2B validation";
            case STK_PUSH -> "STK push";
            case QR -> "dynamic QR codes";
            case PULL_TRANSACTIONS -> "Pull Transactions";
            case REVERSAL -> "reversals through the API";
            case B2C_PAYOUT -> "B2C payouts";
            case POCHI -> "Business to Pochi payouts";
            case TRANSACTION_STATUS -> "Transaction Status queries";
            case ACCOUNT_BALANCE -> "Account Balance queries";
            case B2B_EXPRESS_CHECKOUT -> "B2B Express Checkout (it receives into a paybill)";
        };
    }
}
