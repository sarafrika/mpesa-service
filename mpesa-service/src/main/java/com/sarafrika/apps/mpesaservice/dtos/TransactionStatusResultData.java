package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.dtos.TransactionStatusResultParameters;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transaction status result data within the callback
 */
@Schema(description = "Transaction status result data")
public record TransactionStatusResultData(

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

        @Schema(description = "Transaction ID", example = "LGR019G3J2")
        @JsonProperty("TransactionID")
        String transactionId,

        @Schema(description = "Result parameters with transaction details")
        @JsonProperty("ResultParameters")
        TransactionStatusResultParameters resultParameters
) {

    /**
     * Check if the transaction was successful
     */
    public boolean isSuccessful() {
        return resultCode != null && resultCode == 0;
    }

    /**
     * Check if transaction was not found
     */
    public boolean isNotFound() {
        return resultCode != null && resultCode == 1;
    }
}
