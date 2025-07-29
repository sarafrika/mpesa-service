package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.dtos.TransactionReversalResultParameters;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transaction reversal result data within the callback
 */
@Schema(description = "Transaction reversal result data")
public record TransactionReversalResultData(

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

        @Schema(description = "Result parameters with reversal details")
        @JsonProperty("ResultParameters")
        TransactionReversalResultParameters resultParameters
) {

    /**
     * Check if the reversal was successful
     */
    public boolean isSuccessful() {
        return resultCode != null && resultCode == 0;
    }

    /**
     * Check if transaction was not found
     */
    public boolean isTransactionNotFound() {
        return resultCode != null && resultCode == 1;
    }

    /**
     * Check if reversal was rejected
     */
    public boolean isRejected() {
        return resultCode != null && resultCode == 1037;
    }
}
