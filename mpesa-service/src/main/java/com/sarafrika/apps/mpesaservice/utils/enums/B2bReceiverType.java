package com.sarafrika.apps.mpesaservice.utils.enums;

/** What a B2B destination is; it picks the Daraja CommandID (BusinessPayBill or BusinessBuyGoods). */
public enum B2bReceiverType {
    PAYBILL, TILL;

    public String commandId() {
        return this == PAYBILL ? "BusinessPayBill" : "BusinessBuyGoods";
    }
}
