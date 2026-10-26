package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Safaricom's acknowledgement of a reversal request (developer.safaricom.co.ke/apis/Reversal); the result
 * comes later on the ResultURL. ResponseCode "0" means accepted. */
@Schema(description = "Transaction reversal initiation response from Daraja API")
public record TransactionReversalResponse(

        @Schema(description = "Conversation ID from Safaricom", example = "AG_20210706_20106e9209f64bebd05b")
        @JsonProperty("ConversationID")
        String conversationId,

        @Schema(description = "Originator conversation ID", example = "f1e2-4b95-a71d-b30d3cdbb7a7735297")
        @JsonProperty("OriginatorConversationID")
        @JsonAlias("OriginatorCoversationID")
        String originatorConversationId,

        @Schema(description = "0 when Safaricom accepted the request", example = "0")
        @JsonProperty("ResponseCode")
        String responseCode,

        @Schema(description = "Response description", example = "Accept the service request successfully.")
        @JsonProperty("ResponseDescription")
        String responseDescription
) {

    public boolean isAccepted() {
        return responseCode == null ? conversationId != null : "0".equals(responseCode.trim());
    }
}
