package com.sarafrika.apps.mpesaservice.utils.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** What a shortcode is set up to do. Credentials follow the operations, not the type: C2B needs only the
 * consumer key and secret, STK push the passkey, the rest the API initiator. */
@Schema(enumAsRef = true)
public enum ShortcodeOperation {
    C2B(Needs.NOTHING, "C2B", false, ShortcodeCapability.C2B_REGISTER, ShortcodeCapability.C2B_VALIDATION),
    STK_PUSH(Needs.PASSKEY, "STK push", false, ShortcodeCapability.STK_PUSH),
    REVERSAL(Needs.INITIATOR, "reversals", true, ShortcodeCapability.REVERSAL),
    TRANSACTION_STATUS(Needs.INITIATOR, "Transaction Status queries", true, ShortcodeCapability.TRANSACTION_STATUS),
    ACCOUNT_BALANCE(Needs.INITIATOR, "Account Balance queries", true, ShortcodeCapability.ACCOUNT_BALANCE),
    B2C_PAYOUT(Needs.INITIATOR, "B2C payouts", true, ShortcodeCapability.B2C_PAYOUT),
    POCHI(Needs.INITIATOR, "Business to Pochi payouts", true, ShortcodeCapability.POCHI);

    /** The credential an operation needs beyond the consumer key and secret. */
    public enum Needs { NOTHING, PASSKEY, INITIATOR }

    private final Needs needs;
    private final String label;
    private final boolean plural;
    private final Set<ShortcodeCapability> capabilities;

    ShortcodeOperation(Needs needs, String label, boolean plural, ShortcodeCapability first,
                       ShortcodeCapability... rest) {
        this.needs = needs;
        this.label = label;
        this.plural = plural;
        this.capabilities = Collections.unmodifiableSet(EnumSet.of(first, rest));
    }

    public Needs needs() {
        return needs;
    }

    public Set<ShortcodeCapability> capabilities() {
        return capabilities;
    }

    /** "STK push", "reversals": the operation as written mid-sentence. */
    public String label() {
        return label;
    }

    /** "is" or "are", to agree with the label. */
    public String verb() {
        return plural ? "are" : "is";
    }

    public String needVerb() {
        return plural ? "need" : "needs";
    }

    /** The operation that switches a capability on, or null for the ones always on (QR, Pull, B2B). */
    public static ShortcodeOperation forCapability(ShortcodeCapability capability) {
        for (ShortcodeOperation operation : values()) {
            if (operation.capabilities.contains(capability)) {
                return operation;
            }
        }
        return null;
    }
}
