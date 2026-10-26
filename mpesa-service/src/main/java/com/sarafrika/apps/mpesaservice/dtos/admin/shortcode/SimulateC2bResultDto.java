package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Safaricom accepted the simulation; the payment itself arrives on the confirmation URL. */
@Schema(name = "SimulateC2bResult")
public record SimulateC2bResultDto(
        @JsonProperty("conversation_id") String conversationId,
        @JsonProperty("originator_conversation_id") String originatorConversationId,
        @Schema(example = "Accept the service request successfully.")
        @JsonProperty("response_description") String responseDescription) {
}
