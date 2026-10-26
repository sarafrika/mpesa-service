package com.sarafrika.apps.mpesaservice.utils.enums;

import java.util.Locale;

/** How a C2B payment reached us: Pay Bill (with an account number) or Buy Goods (a till). */
public enum C2BChannel {
    PAYBILL, BUY_GOODS;

    /** Reads Daraja's TransType ("Pay Bill", "Buy Goods", "CustomerBuyGoodsOnline"...); null when unclear. */
    public static C2BChannel fromTransType(String transType) {
        if (transType == null) {
            return null;
        }
        String normalized = transType.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (normalized.contains("goods") || normalized.contains("till")) {
            return BUY_GOODS;
        }
        if (normalized.contains("bill")) {
            return PAYBILL;
        }
        return null;
    }
}
