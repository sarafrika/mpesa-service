package com.sarafrika.apps.mpesaservice.controllers;

import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaIncomingPaymentDto;
import com.sarafrika.apps.mpesaservice.dtos.StkPushRequest;
import com.sarafrika.apps.mpesaservice.dtos.StkPushResponse;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Generic, shortcode-driven M-Pesa payment API.
 * <p>
 * Lets any consuming system initiate STK Push payments against any configured
 * shortcode and poll the resulting payment status. Nothing here is tied to a
 * specific business, till or paybill.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/mpesa")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "M-Pesa Payments", description = "Initiate STK Push payments and poll incoming payment status")
public class MpesaPaymentController {

    private final MpesaDarajaService mpesaDarajaService;
    private final MpesaIncomingPaymentService incomingPaymentService;

    /**
     * Initiate an STK Push against a configured shortcode.
     */
    @PostMapping("/stk-push")
    @Operation(summary = "Initiate an STK Push payment for a configured shortcode")
    public ResponseEntity<MpesaApiResponse<StkPushResponse>> initiateStkPush(
            @RequestBody StkPushRequest request) {

        log.info("Received STK Push initiation request for shortcode: {}, phone: {}, amount: {}",
                request.shortcodeUuid(), request.phoneNumber(), request.amount());

        MpesaApiResponse<StkPushResponse> response = mpesaDarajaService.initiateSTKPush(
                request.shortcodeUuid(),
                request.phoneNumber(),
                request.amount(),
                request.accountReference(),
                request.transactionDesc());

        return ResponseEntity.status(response.httpStatus()).body(response);
    }

    /**
     * Look up an incoming payment by its checkout request id (STK Push).
     * Consuming systems poll this endpoint to determine the final payment status.
     */
    @GetMapping("/payments/by-checkout/{checkoutRequestId}")
    @Operation(summary = "Fetch an incoming payment by STK Push checkout request id")
    public ResponseEntity<MpesaIncomingPaymentDto> getPaymentByCheckoutRequestId(
            @PathVariable String checkoutRequestId) {

        return incomingPaymentService.findByCheckoutRequestId(checkoutRequestId)
                .map(MpesaIncomingPaymentDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Look up incoming payments by account reference. Useful when a consuming
     * system tracks payments by its own reference rather than a checkout id.
     */
    @GetMapping("/payments")
    @Operation(summary = "Fetch incoming payments by account reference or M-Pesa transaction id")
    public ResponseEntity<List<MpesaIncomingPaymentDto>> getPayments(
            @RequestParam(name = "account_reference", required = false) String accountReference,
            @RequestParam(name = "transaction_id", required = false) String transactionId) {

        if (transactionId != null && !transactionId.isBlank()) {
            Optional<MpesaIncomingPayment> payment = incomingPaymentService.findByTransactionId(transactionId);
            return ResponseEntity.ok(payment.map(MpesaIncomingPaymentDto::from).map(List::of).orElseGet(List::of));
        }

        if (accountReference == null || accountReference.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<MpesaIncomingPaymentDto> results = incomingPaymentService.findByAccountReference(accountReference).stream()
                .map(MpesaIncomingPaymentDto::from)
                .toList();

        return ResponseEntity.ok(results);
    }
}
