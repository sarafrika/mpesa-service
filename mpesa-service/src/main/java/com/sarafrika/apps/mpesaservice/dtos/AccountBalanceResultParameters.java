package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Account balance result parameters containing detailed balance information
 */
@Schema(description = "Account balance result parameters")
public record AccountBalanceResultParameters(

        @Schema(description = "List of result parameter items")
        @JsonProperty("ResultParameter")
        List<AccountBalanceResultParameter> resultParameters
) {

    /**
     * Get specific parameter value by name
     */
    public String getParameterValue(String name) {
        return resultParameters.stream()
                .filter(param -> name.equals(param.key()))
                .map(AccountBalanceResultParameter::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get account balance
     */
    public String getAccountBalance() {
        return getParameterValue("AccountBalance");
    }

    /**
     * Get booked funds
     */
    public String getBookedFunds() {
        return getParameterValue("BOCompletedAmount");
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
     * Get uncleared balance
     */
    public String getUnclearedBalance() {
        return getParameterValue("UnclearedBalance");
    }

    /**
     * Get float account available funds
     */
    public String getFloatAccountAvailableFunds() {
        return getParameterValue("FloatAccountAvailableFunds");
    }
}
