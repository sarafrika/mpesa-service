package com.sarafrika.apps.mpesaservice.controllers;


import com.sarafrika.apps.mpesaservice.services.collections.TransactionStatusService;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.treasury.B2bTransferService;
import com.sarafrika.apps.mpesaservice.services.treasury.BalanceService;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.sarafrika.apps.mpesaservice.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handling B2C and B2B callback responses from M-Pesa Daraja API
 * <p>
 * This controller handles result and timeout callbacks for:
 * <ul>
 * <li>B2C (Business to Customer) payments</li>
 * <li>B2B (Business to Business) transfers</li>
 * <li>Transaction Status Queries</li>
 * <li>Account Balance Queries</li>
 * <li>Transaction Reversals</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/callbacks")
@RequiredArgsConstructor
@Slf4j
public class B2CB2BCallbackController {

    private final MpesaPayoutService payoutService;
    private final TransactionStatusService transactionStatusService;
    private final BalanceService balanceService;
    private final B2bTransferService b2bTransferService;
    private final ObjectMapper objectMapper;

    // ==================== B2C CALLBACKS ====================

    /**
     * Handle B2C payment result callback
     * Called when B2C payment processing completes (success or failure)
     */
    @PostMapping("/b2c/result")
    public ResponseEntity<CallbackAcknowledgment> handleB2CResult(
            @RequestBody B2CPaymentResult paymentResult) {

        try {
            log.info("Received B2C payment result - ConversationID: {}, ResultCode: {}",
                    paymentResult.result().conversationId(),
                    paymentResult.result().resultCode());

            B2CResult result = paymentResult.result();
            payoutService.applyResult(result, objectMapper.writeValueAsString(paymentResult));
            return ResponseEntity.ok(CallbackAcknowledgment.success());

        } catch (Exception e) {
            log.error("Error processing B2C result callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    /**
     * Handle B2C payment timeout callback
     * Called when B2C payment request times out
     */
    @PostMapping("/b2c/timeout")
    public ResponseEntity<CallbackAcknowledgment> handleB2CTimeout(
            @RequestBody B2CTimeoutCallback timeoutCallback) {

        try {
            log.warn("Received B2C timeout - ConversationID: {}, ResultDesc: {}",
                    timeoutCallback.conversationId(),
                    timeoutCallback.resultDesc());

            // A queue timeout leaves the outcome unknown: the payout stays pending and is flagged.
            payoutService.applyTimeout(timeoutCallback);
            log.info("Processed B2C timeout for ConversationID: {}", timeoutCallback.conversationId());
            return ResponseEntity.ok(CallbackAcknowledgment.success());

        } catch (Exception e) {
            log.error("Error processing B2C timeout callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    // ==================== B2B CALLBACKS ====================

    /** The B2B result settles the sweep it belongs to, once (a repeat is acknowledged and ignored). */
    @PostMapping("/b2b/result")
    public ResponseEntity<CallbackAcknowledgment> handleB2BResult(
            @RequestBody B2BTransferResult transferResult) {
        try {
            B2BResult result = transferResult.result();
            log.info("Received B2B result - ConversationID: {}, ResultCode: {}", result.conversationId(),
                    result.resultCode());
            b2bTransferService.applyResult(result, objectMapper.writeValueAsString(transferResult));
            return ResponseEntity.ok(CallbackAcknowledgment.success());
        } catch (Exception e) {
            log.error("Error processing B2B result callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    /** A queue timeout leaves the sweep's outcome unknown: it stays PENDING, flagged, and is never resent. */
    // Daraja does not document the timeout body, so both a flat one and one wrapped in "Result" are read.
    @PostMapping("/b2b/timeout")
    public ResponseEntity<CallbackAcknowledgment> handleB2BTimeout(@RequestBody Map<String, Object> body) {
        TimeoutIds ids = TimeoutIds.of(body);
        log.warn("Received B2B timeout - ConversationID: {}", ids.conversationId());
        try {
            b2bTransferService.applyTimeout(ids.originatorConversationId(), ids.conversationId(), ids.description());
            return ResponseEntity.ok(CallbackAcknowledgment.success());
        } catch (Exception e) {
            log.error("Error processing B2B timeout callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    // ==================== TRANSACTION STATUS CALLBACKS ====================

    /**
     * Handle transaction status query result callback
     * Called with the status of a queried transaction
     */
    @PostMapping("/transaction-status/result")
    public ResponseEntity<CallbackAcknowledgment> handleTransactionStatusResult(
            @RequestBody TransactionStatusResult statusResult) {

        try {
            log.info("Received transaction status result - ConversationID: {}, ResultCode: {}",
                    statusResult.result().conversationId(),
                    statusResult.result().resultCode());

            transactionStatusService.applyResult(statusResult.result(), objectMapper.writeValueAsString(statusResult));
            return ResponseEntity.ok(CallbackAcknowledgment.success());

        } catch (Exception e) {
            log.error("Error processing transaction status result callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    /** Safaricom could not process a status query in time; the query is marked timed out. */
    // Accepts both the flat timeout shape and one wrapped in "Result", as Daraja examples differ.
    @PostMapping("/transaction-status/timeout")
    public ResponseEntity<CallbackAcknowledgment> handleTransactionStatusTimeout(
            @RequestBody java.util.Map<String, Object> body) {
        Object wrapped = body.get("Result");
        java.util.Map<?, ?> result = wrapped instanceof java.util.Map<?, ?> map ? map : body;
        Object conversationId = result.get("ConversationID");
        Object originatorId = result.get("OriginatorConversationID");
        log.warn("Transaction status timeout - ConversationID: {}", conversationId);
        try {
            transactionStatusService.applyTimeout(conversationId == null ? null : conversationId.toString(),
                    originatorId == null ? null : originatorId.toString());
            return ResponseEntity.ok(CallbackAcknowledgment.success());
        } catch (Exception e) {
            log.error("Error processing transaction status timeout callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    // ==================== ACCOUNT BALANCE CALLBACKS ====================

    /** The Account Balance result fills the snapshot of the query it answers. */
    @PostMapping("/account-balance/result")
    public ResponseEntity<CallbackAcknowledgment> handleAccountBalanceResult(
            @RequestBody AccountBalanceResult balanceResult) {
        try {
            AccountBalanceResultData result = balanceResult.result();
            log.info("Received account balance result - ConversationID: {}, ResultCode: {}",
                    result.conversationId(), result.resultCode());
            balanceService.applyResult(result, objectMapper.writeValueAsString(balanceResult));
            return ResponseEntity.ok(CallbackAcknowledgment.success());
        } catch (Exception e) {
            log.error("Error processing account balance result callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    /** Safaricom could not process a balance query in time; the snapshot is marked TIMED_OUT. */
    @PostMapping("/account-balance/timeout")
    public ResponseEntity<CallbackAcknowledgment> handleAccountBalanceTimeout(@RequestBody Map<String, Object> body) {
        TimeoutIds ids = TimeoutIds.of(body);
        log.warn("Account balance timeout - ConversationID: {}", ids.conversationId());
        try {
            balanceService.applyTimeout(ids.originatorConversationId(), ids.conversationId(), ids.description());
            return ResponseEntity.ok(CallbackAcknowledgment.success());
        } catch (Exception e) {
            log.error("Error processing account balance timeout callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    /** The conversation ids of a timeout body, flat or wrapped in "Result". */
    record TimeoutIds(String originatorConversationId, String conversationId, String description) {
        static TimeoutIds of(Map<String, Object> body) {
            Object wrapped = body.get("Result");
            Map<?, ?> result = wrapped instanceof Map<?, ?> map ? map : body;
            return new TimeoutIds(string(result.get("OriginatorConversationID")),
                    string(result.get("ConversationID")), string(result.get("ResultDesc")));
        }

        private static String string(Object value) {
            return value == null ? null : value.toString();
        }
    }

    // ==================== TRANSACTION REVERSAL CALLBACKS ====================

    /**
     * Handle transaction reversal result callback
     * Called when transaction reversal processing completes
     */
    @PostMapping("/reversal/result")
    public ResponseEntity<CallbackAcknowledgment> handleReversalResult(
            @RequestBody TransactionReversalResult reversalResult) {

        try {
            log.info("Received reversal result - ConversationID: {}, ResultCode: {}",
                    reversalResult.result().conversationId(),
                    reversalResult.result().resultCode());

            TransactionReversalResultData result = reversalResult.result();

            if (result.isSuccessful()) {
                handleSuccessfulReversal(result);
            } else if (result.isTransactionNotFound()) {
                handleReversalTransactionNotFound(result);
            } else if (result.isRejected()) {
                handleReversalRejected(result);
            } else {
                handleReversalError(result);
            }

            return ResponseEntity.ok(CallbackAcknowledgment.success());

        } catch (Exception e) {
            log.error("Error processing reversal result callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    /**
     * Handle transaction reversal timeout callback
     * Called when reversal request times out
     */
    @PostMapping("/reversal/timeout")
    public ResponseEntity<CallbackAcknowledgment> handleReversalTimeout(
            @RequestBody TransactionReversalTimeoutCallback timeoutCallback) {

        try {
            log.warn("Received reversal timeout - ConversationID: {}, ResultDesc: {}",
                    timeoutCallback.conversationId(),
                    timeoutCallback.resultDesc());

            // Handle reversal timeout
            // TODO: Mark reversal as timed out
            // TODO: Implement manual intervention process

            log.info("Processed reversal timeout for ConversationID: {}", timeoutCallback.conversationId());
            return ResponseEntity.ok(CallbackAcknowledgment.success());

        } catch (Exception e) {
            log.error("Error processing reversal timeout callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    // ==================== CALLBACK PROCESSING METHODS ====================



    private void handleSuccessfulReversal(TransactionReversalResultData result) {
        log.info("Processing successful reversal - TransactionID: {}", result.transactionId());

        // Extract reversal details
        TransactionReversalResultParameters params = result.resultParameters();
        if (params != null) {
            String amount = params.getAmount();
            String reversalType = params.getReversalType();
            String receiverName = params.getReceiverPartyPublicName();
            String completedDateTime = params.getTransactionCompletedDateTime();

            log.info("Reversal Details - Amount: {}, Type: {}, Receiver: {}, Completed: {}",
                    amount, reversalType, receiverName, completedDateTime);

            // TODO: Update original transaction status to reversed
            // TODO: Update account balances
            // TODO: Send reversal confirmation to affected parties
        }
    }

    private void handleReversalTransactionNotFound(TransactionReversalResultData result) {
        log.warn("Reversal failed - Transaction not found: {}", result.resultDesc());

        // TODO: Mark reversal as failed - transaction not found
        // TODO: Notify requesting party
    }

    private void handleReversalRejected(TransactionReversalResultData result) {
        log.warn("Reversal rejected - ResultDesc: {}", result.resultDesc());

        // TODO: Mark reversal as rejected
        // TODO: Notify requesting party with rejection reason
    }

    private void handleReversalError(TransactionReversalResultData result) {
        log.error("Reversal error - ResultDesc: {}", result.resultDesc());

        // TODO: Mark reversal as failed
        // TODO: Implement retry logic or manual intervention
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Generic callback acknowledgment response
     */
    public record CallbackAcknowledgment(
            @com.fasterxml.jackson.annotation.JsonProperty("ResultCode")
            String resultCode,

            @com.fasterxml.jackson.annotation.JsonProperty("ResultDesc")
            String resultDesc
    ) {

        public static CallbackAcknowledgment success() {
            return new CallbackAcknowledgment("00000000", "Success");
        }

        public static CallbackAcknowledgment error() {
            return new CallbackAcknowledgment("00000001", "Internal server error");
        }
    }
}