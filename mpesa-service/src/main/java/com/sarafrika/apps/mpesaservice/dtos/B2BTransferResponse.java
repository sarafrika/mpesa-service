package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response from B2B transfer initiation
 * Returned when initiating a Business to Business transfer
 */
@Schema(description = "B2B transfer initiation response from Daraja API")
public record B2BTransferResponse(

        @Schema(description = "Conversation ID from Safaricom", example = "AG_20230727_2010400e808f7bb5f4")
        @JsonProperty("ConversationID")
        String conversationId,

        @Schema(description = "Originator conversation ID", example = "21605-295434-4")
        @JsonProperty("OriginatorConversationID")
        @JsonAlias("OriginatorCoversationID")
        String originatorConversationId,

        @Schema(description = "Response code; 0 means Safaricom accepted the request", example = "0")
        @JsonProperty("ResponseCode")
        String responseCode,

        @Schema(description = "Response description", example = "Accept the service request successfully.")
        @JsonProperty("ResponseDescription")
        String responseDescription
) {

    public boolean isAccepted() {
        return responseCode == null || "0".equals(responseCode.trim());
    }
}