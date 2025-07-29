package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Account balance result data within the callback
 */
@Schema(description = "Account balance result data")
public record AccountBalanceResultData(

        @Schema(description = "Result type", example = "Result")
        @JsonProperty("ResultType")
        Integer resultType,

        @Schema(description = "Result code (0 = success)", example = "0")
        @JsonProperty("ResultCode")
        Integer resultCode,

        @Schema(description = "Result description", example = "The service request is processed successfully.")
        @JsonProperty("ResultDesc")
        String resultDesc,

        @Schema(description = "Originator conversation ID", example = "21605-295434-4")
        @JsonProperty("OriginatorConversationID")
        String originatorConversationId,

        @Schema(description = "Conversation ID", example = "AG_20230727_2010400e808f7bb5f4")
        @JsonProperty("ConversationID")
        String conversationId,

        @Schema(description = "Result parameters with balance details")
        @JsonProperty("ResultParameters")
        AccountBalanceResultParameters resultParameters
) {

    /**
     * Check if the query was successful
     */
    public boolean isSuccessful() {
        return resultCode != null && resultCode == 0;
    }
}
