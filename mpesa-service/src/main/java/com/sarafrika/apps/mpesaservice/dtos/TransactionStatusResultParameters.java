package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Transaction status result parameters containing detailed information
 */
@Schema(description = "Transaction status result parameters")
public record TransactionStatusResultParameters(

        @Schema(description = "List of result parameter items")
        @JsonProperty("ResultParameter")
        List<TransactionStatusResultParameter> resultParameters
) {

    /**
     * Get specific parameter value by name
     */
    public String getParameterValue(String name) {
        return resultParameters.stream()
                .filter(param -> name.equals(param.key()))
                .map(TransactionStatusResultParameter::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get receipt number
     */
    public String getReceiptNumber() {
        return getParameterValue("ReceiptNo");
    }

    /**
     * Get transaction amount
     */
    public String getTransactionAmount() {
        return getParameterValue("TransactionAmount");
    }

    /**
     * Get balance
     */
    public String getBalance() {
        return getParameterValue("Balance");
    }

    /**
     * Get working account available funds
     */
    public String getWorkingAccountAvailableFunds() {
        return getParameterValue("WorkingAccountAvailableFunds");
    }

    /**
     * Get utility account available funds
     */
    public String getUtilityAccountAvailableFunds() {
        return getParameterValue("UtilityAccountAvailableFunds");
    }

    /**
     * Get charges paid account available funds
     */
    public String getChargesPaidAccountAvailableFunds() {
        return getParameterValue("ChargesPaidAccountAvailableFunds");
    }

    /**
     * Get recipient registered
     */
    public String getRecipientRegistered() {
        return getParameterValue("RecipientRegistered");
    }

    /**
     * Get transaction status
     */
    public String getTransactionStatus() {
        return getParameterValue("TransactionStatus");
    }

    /**
     * Get reason type
     */
    public String getReasonType() {
        return getParameterValue("ReasonType");
    }

    /**
     * Get debit party charges
     */
    public String getDebitPartyCharges() {
        return getParameterValue("DebitPartyCharges");
    }

    /**
     * Get debit account type
     */
    public String getDebitAccountType() {
        return getParameterValue("DebitAccountType");
    }

    /**
     * Get initiator account current balance
     */
    public String getInitiatorAccountCurrentBalance() {
        return getParameterValue("InitiatorAccountCurrentBalance");
    }

    /**
     * Get completed time
     */
    public String getCompletedTime() {
        return getParameterValue("CompletedTime");
    }
}
