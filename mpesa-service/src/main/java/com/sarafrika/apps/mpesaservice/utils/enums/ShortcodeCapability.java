package com.sarafrika.apps.mpesaservice.utils.enums;

/** What a shortcode can do with Daraja; {@code ShortcodeCapabilities.of(type)} says which a type has. */
public enum ShortcodeCapability {
    C2B_REGISTER, C2B_VALIDATION, STK_PUSH, QR, PULL_TRANSACTIONS, REVERSAL, B2C_PAYOUT, POCHI,
    TRANSACTION_STATUS, ACCOUNT_BALANCE, B2B_EXPRESS_CHECKOUT
}
