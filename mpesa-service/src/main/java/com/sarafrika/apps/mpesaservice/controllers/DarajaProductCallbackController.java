package com.sarafrika.apps.mpesaservice.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.controllers.B2CB2BCallbackController.CallbackAcknowledgment;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackOutcome;
import com.sarafrika.apps.mpesaservice.services.daraja.products.B2bCheckoutService;
import com.sarafrika.apps.mpesaservice.services.daraja.products.ProductPayoutService;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Safaricom's results for the flagged products: Tax Remittance, Business to Pochi and B2B Express Checkout.
 * Each settles its record once; a repeat is acknowledged and ignored. Bodies are read as JSON because
 * ResultCode may be a number or a string such as SFC_IC0003. */
@Hidden
@Slf4j
@RestController
@RequestMapping("/api/v1/callbacks")
@RequiredArgsConstructor
public class DarajaProductCallbackController {

    private final ProductPayoutService payouts;
    private final B2bCheckoutService checkouts;
    private final ObjectMapper objectMapper;

    @PostMapping("/tax-remittance/result")
    public ResponseEntity<CallbackAcknowledgment> handleTaxRemittanceResult(@RequestBody JsonNode body) {
        return result(DarajaFeature.TAX_REMITTANCE, body);
    }

    @PostMapping("/tax-remittance/timeout")
    public ResponseEntity<CallbackAcknowledgment> handleTaxRemittanceTimeout(@RequestBody JsonNode body) {
        return timeout(DarajaFeature.TAX_REMITTANCE, body);
    }

    @PostMapping("/pochi/result")
    public ResponseEntity<CallbackAcknowledgment> handlePochiResult(@RequestBody JsonNode body) {
        return result(DarajaFeature.BUSINESS_TO_POCHI, body);
    }

    @PostMapping("/pochi/timeout")
    public ResponseEntity<CallbackAcknowledgment> handlePochiTimeout(@RequestBody JsonNode body) {
        return timeout(DarajaFeature.BUSINESS_TO_POCHI, body);
    }

    @PostMapping("/b2b-checkout/result")
    public ResponseEntity<CallbackAcknowledgment> handleB2bCheckoutResult(@RequestBody JsonNode body) {
        try {
            log.info("Received B2B Express Checkout callback - requestId: {}, resultCode: {}",
                    body.path("requestId").asText(null), body.path("resultCode").asText(null));
            checkouts.applyResult(body, objectMapper.writeValueAsString(body));
            return ResponseEntity.ok(CallbackAcknowledgment.success());
        } catch (Exception e) {
            log.error("Error processing B2B Express Checkout callback", e);
            CallbackOutcome.failed(e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    private ResponseEntity<CallbackAcknowledgment> result(DarajaFeature product, JsonNode body) {
        try {
            JsonNode result = body.path("Result");
            log.info("Received {} result - ConversationID: {}, ResultCode: {}", product,
                    result.path("ConversationID").asText(null), result.path("ResultCode").asText(null));
            payouts.applyResult(product, body, objectMapper.writeValueAsString(body));
            return ResponseEntity.ok(CallbackAcknowledgment.success());
        } catch (Exception e) {
            log.error("Error processing {} result callback", product, e);
            CallbackOutcome.failed(e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    // A queue timeout leaves the outcome unknown; the payment is flagged and never resent.
    private ResponseEntity<CallbackAcknowledgment> timeout(DarajaFeature product, JsonNode body) {
        try {
            log.warn("Received {} timeout", product);
            payouts.applyTimeout(product, body);
            return ResponseEntity.ok(CallbackAcknowledgment.success());
        } catch (Exception e) {
            log.error("Error processing {} timeout callback", product, e);
            CallbackOutcome.failed(e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }
}
