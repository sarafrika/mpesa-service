package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Response from C2B URL registration
 * Returned when registering confirmation and validation URLs
 */
@Schema(description = "C2B URL registration response from Daraja API")
public record C2BRegistrationResponse(

        @Schema(description = "Conversation ID from Safaricom", example = "AG_20230727_2010400e808f7bb5f4")
        @JsonProperty("ConversationID")
        String conversationId,

        @Schema(description = "Originator conversation ID", example = "21605-295434-4")
        @JsonProperty("OriginatorCoversationID")
        String originatorConversationId,

        @Schema(description = "Response description", example = "Success")
        @JsonProperty("ResponseDescription")
        String responseDescription
) {}