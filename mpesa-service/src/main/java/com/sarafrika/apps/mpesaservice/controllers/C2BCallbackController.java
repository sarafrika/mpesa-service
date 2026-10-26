package com.sarafrika.apps.mpesaservice.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.C2BConfirmationCallback;
import com.sarafrika.apps.mpesaservice.dtos.C2BValidationRequest;
import com.sarafrika.apps.mpesaservice.dtos.C2BValidationResponse;
import com.sarafrika.apps.mpesaservice.services.collections.C2BConfirmationService;
import com.sarafrika.apps.mpesaservice.services.collections.C2BResultCodes;
import com.sarafrika.apps.mpesaservice.services.collections.C2BValidationService;
import com.sarafrika.apps.mpesaservice.services.collections.ValidationDecision;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Safaricom's C2B callbacks: validation (accept or reject before the money moves) and confirmation (after). */
@RestController
@RequestMapping("/api/v1/callbacks/c2b")
@RequiredArgsConstructor
@Slf4j
public class C2BCallbackController {

    private final C2BValidationService validationService;
    private final C2BConfirmationService confirmationService;
    private final ObjectMapper objectMapper;

    @PostMapping("/validation")
    public ResponseEntity<C2BValidationResponse> handleC2BValidation(@RequestBody C2BValidationRequest request) {
        log.info("C2B validation - TransID: {}, Amount: {}, BillRefNumber: {}, ShortCode: {}",
                request.transId(), request.transAmount(), request.billRefNumber(), request.businessShortCode());
        try {
            ValidationDecision decision = validationService.validate(request);
            if (decision.accepted()) {
                return ResponseEntity.ok(C2BValidationResponse.accept());
            }
            log.warn("C2B validation rejected TransID {} with {}: {}", request.transId(), decision.resultCode(),
                    decision.reason());
            return ResponseEntity.ok(C2BValidationResponse.reject(decision.resultCode()));
        } catch (Exception e) {
            log.error("Error during C2B validation for TransID: {}", request.transId(), e);
            return ResponseEntity.ok(C2BValidationResponse.reject(C2BResultCodes.OTHER_ERROR));
        }
    }

    /** Always acknowledged once stored, so a retried confirmation is answered without storing it twice. */
    @PostMapping("/confirmation")
    public ResponseEntity<C2BConfirmationAcknowledgment> handleC2BConfirmation(@RequestBody Map<String, Object> body) {
        C2BConfirmationCallback callback;
        try {
            callback = objectMapper.convertValue(body, C2BConfirmationCallback.class);
        } catch (IllegalArgumentException e) {
            log.error("Unreadable C2B confirmation: {}", e.getMessage());
            return ResponseEntity.ok(C2BConfirmationAcknowledgment.error());
        }
        log.info("C2B confirmation - TransID: {}, Amount: {}, BillRefNumber: {}, ShortCode: {}",
                callback.transId(), callback.transAmount(), callback.billRefNumber(), callback.businessShortCode());
        try {
            confirmationService.confirm(callback, body);
            return ResponseEntity.ok(C2BConfirmationAcknowledgment.success());
        } catch (DataIntegrityViolationException e) {
            // Two deliveries of the same TransID raced; the other one stored it.
            if (confirmationService.findByTransId(callback.transId()).isPresent()) {
                return ResponseEntity.ok(C2BConfirmationAcknowledgment.success());
            }
            log.error("Could not store C2B confirmation {}", callback.transId(), e);
            return ResponseEntity.ok(C2BConfirmationAcknowledgment.error());
        } catch (Exception e) {
            log.error("Error processing C2B confirmation for TransID: {}", callback.transId(), e);
            return ResponseEntity.ok(C2BConfirmationAcknowledgment.error());
        }
    }

    public record C2BConfirmationAcknowledgment(
            @JsonProperty("ResultCode") String resultCode,
            @JsonProperty("ResultDesc") String resultDesc) {

        static C2BConfirmationAcknowledgment success() {
            return new C2BConfirmationAcknowledgment("0", "Success");
        }

        static C2BConfirmationAcknowledgment error() {
            return new C2BConfirmationAcknowledgment("00000001", "Internal processing error");
        }
    }
}
