package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * B2B result parameters containing detailed transaction information
 */
@Schema(description = "B2B result parameters")
public record B2BResultParameters(

        @Schema(description = "List of result parameter items")
        @JsonProperty("ResultParameter")
        List<B2BResultParameter> resultParameters
) {

    /**
     * Get specific parameter value by name
     */
    public String getParameterValue(String name) {
        return resultParameters.stream()
                .filter(param -> name.equals(param.key()))
                .map(B2BResultParameter::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get initiator account current balance
     */
    public String getInitiatorAccountCurrentBalance() {
        return getParameterValue("InitiatorAccountCurrentBalance");
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
     * Get debit party charges
     */
    public String getDebitPartyCharges() {
        return getParameterValue("DebitPartyCharges");
    }

    /**
     * Get receiver party public name
     */
    public String getReceiverPartyPublicName() {
        return getParameterValue("ReceiverPartyPublicName");
    }

    /**
     * Get currency
     */
    public String getCurrency() {
        return getParameterValue("Currency");
    }
}
