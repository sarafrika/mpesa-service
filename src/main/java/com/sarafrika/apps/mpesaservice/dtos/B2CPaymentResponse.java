package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response from B2C payment initiation
 * Returned when initiating a Business to Customer payment
 */
@Schema(description = "B2C payment initiation response from Daraja API")
public record B2CPaymentResponse(

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