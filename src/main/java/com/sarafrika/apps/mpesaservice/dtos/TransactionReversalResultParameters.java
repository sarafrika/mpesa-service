package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Transaction reversal result parameters containing detailed reversal information
 */
@Schema(description = "Transaction reversal result parameters")
public record TransactionReversalResultParameters(

        @Schema(description = "List of result parameter items")
        @JsonProperty("ResultParameter")
        List<TransactionReversalResultParameter> resultParameters
) {

    /**
     * Get specific parameter value by name
     */
    public String getParameterValue(String name) {
        return resultParameters.stream()
                .filter(param -> name.equals(param.key()))
                .map(TransactionReversalResultParameter::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get originator account current balance
     */
    public String getOriginatorAccountCurrentBalance() {
        return getParameterValue("OriginatorAccountCurrentBalance");
    }

    /**
     * Get debit account current balance
     */
    public String getDebitAccountCurrentBalance() {
        return getParameterValue("DebitAccountCurrentBalance");
    }

    /**
     * Get amount
     */
    public String getAmount() {
        return getParameterValue("Amount");
    }

    /**
     * Get reversal type
     */
    public String getReversalType() {
        return getParameterValue("ReversalType");
    }

    /**
     * Get charges paid account available funds
     */
    public String getChargesPaidAccountAvailableFunds() {
        return getParameterValue("ChargesPaidAccountAvailableFunds");
    }

    /**
     * Get receiver party public name
     */
    public String getReceiverPartyPublicName() {
        return getParameterValue("ReceiverPartyPublicName");
    }

    /**
     * Get transaction completed date time
     */
    public String getTransactionCompletedDateTime() {
        return getParameterValue("TransactionCompletedDateTime");
    }

    /**
     * Get credit party charges
     */
    public String getCreditPartyCharges() {
        return getParameterValue("CreditPartyCharges");
    }

    /**
     * Get debit party charges
     */
    public String getDebitPartyCharges() {
        return getParameterValue("DebitPartyCharges");
    }
}
