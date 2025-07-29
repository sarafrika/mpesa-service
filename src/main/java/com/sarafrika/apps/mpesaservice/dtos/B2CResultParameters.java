package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * B2C result parameters containing detailed transaction information
 */
@Schema(description = "B2C result parameters")
public record B2CResultParameters(

        @Schema(description = "List of result parameter items")
        @JsonProperty("ResultParameter")
        List<B2CResultParameter> resultParameters
) {

    /**
     * Get specific parameter value by name
     */
    public String getParameterValue(String name) {
        return resultParameters.stream()
                .filter(param -> name.equals(param.key()))
                .map(B2CResultParameter::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get transaction receipt
     */
    public String getTransactionReceipt() {
        return getParameterValue("TransactionReceipt");
    }

    /**
     * Get transaction amount
     */
    public String getTransactionAmount() {
        return getParameterValue("TransactionAmount");
    }

    /**
     * Get B2C charges paid amount
     */
    public String getB2CChargesPaidAmount() {
        return getParameterValue("B2CChargesPaidAmount");
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
     * Get B2C utility account available funds
     */
    public String getB2CUtilityAccountAvailableFunds() {
        return getParameterValue("B2CUtilityAccountAvailableFunds");
    }

    /**
     * Get B2C working account available funds
     */
    public String getB2CWorkingAccountAvailableFunds() {
        return getParameterValue("B2CWorkingAccountAvailableFunds");
    }
}