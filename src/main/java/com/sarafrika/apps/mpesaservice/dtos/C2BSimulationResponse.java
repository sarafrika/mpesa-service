package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response from C2B payment simulation (sandbox only)
 * Used for testing C2B payments in sandbox environment
 */
@Schema(description = "C2B payment simulation response from Daraja API")
public record C2BSimulationResponse(

        @Schema(description = "Conversation ID from Safaricom", example = "AG_20230727_2010400e808f7bb5f4")
        @JsonProperty("ConversationID")
        String conversationId,

        @Schema(description = "Originator conversation ID", example = "21605-295434-4")
        @JsonProperty("OriginatorCoversationID")
        String originatorConversationId,

        @Schema(description = "Response description", example = "Accept the service request successfully.")
        @JsonProperty("ResponseDescription")
        String responseDescription
) {}