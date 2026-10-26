package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Safaricom sent the prompt; the payment shows up in transactions once the customer answers. */
@Schema(name = "TestStkResult")
public record TestStkResultDto(
        @Schema(example = "ws_CO_04112017184930742")
        @JsonProperty("checkout_request_id") String checkoutRequestId,
        @Schema(example = "21605-295434-4")
        @JsonProperty("merchant_request_id") String merchantRequestId,
        @Schema(example = "Success. Request accepted for processing")
        @JsonProperty("customer_message") String customerMessage) {
}
