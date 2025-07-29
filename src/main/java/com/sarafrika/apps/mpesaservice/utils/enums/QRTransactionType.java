package com.sarafrika.apps.mpesaservice.utils.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * QR Transaction Type enum for different payment types
 */
public enum QRTransactionType {

    @Schema(description = "Buy Goods - for Till Numbers")
    BUY_GOODS("BG", "Buy Goods", "Till Number"),

    @Schema(description = "Pay Bill - for Paybill Numbers")
    PAY_BILL("PB", "Pay Bill", "Paybill Number"),

    @Schema(description = "Send Money - for Phone Numbers")
    SEND_MONEY("SM", "Send Money", "Phone Number"),

    @Schema(description = "Withdraw - for Agent Numbers")
    WITHDRAW("WA", "Withdraw", "Agent Number"),

    @Schema(description = "Send to Business - for Business Numbers")
    SEND_TO_BUSINESS("SB", "Send to Business", "Business Number");

    private final String code;
    private final String displayName;
    private final String targetType;

    QRTransactionType(String code, String displayName, String targetType) {
        this.code = code;
        this.displayName = displayName;
        this.targetType = targetType;
    }

    /**
     * Get the transaction code used in API calls
     */
    public String getCode() {
        return code;
    }

    /**
     * Get human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the type of target this transaction type uses
     */
    public String getTargetType() {
        return targetType;
    }

    /**
     * Get QRTransactionType from code
     */
    public static QRTransactionType fromCode(String code) {
        for (QRTransactionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown QR transaction type code: " + code);
    }
}