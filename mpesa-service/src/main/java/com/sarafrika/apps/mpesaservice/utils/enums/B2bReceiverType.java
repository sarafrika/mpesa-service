package com.sarafrika.apps.mpesaservice.utils.enums;

/** What a B2B destination is; it picks the Daraja CommandID: BusinessPayBill, BusinessBuyGoods, or
 * BusinessPayToBulk for a B2C Account Top Up of one of our own B2C shortcodes. */
public enum B2bReceiverType {
    PAYBILL("BusinessPayBill"), TILL("BusinessBuyGoods"), B2C_SHORTCODE("BusinessPayToBulk");

    private final String commandId;

    B2bReceiverType(String commandId) {
        this.commandId = commandId;
    }

    public String commandId() {
        return commandId;
    }

    public boolean topUp() {
        return this == B2C_SHORTCODE;
    }
}
