package com.sarafrika.apps.mpesaservice.controllers;


import com.sarafrika.apps.mpesaservice.services.collections.TransactionStatusService;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;

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
    private final ObjectMapper objectMapper;

    // ==================== B2C CALLBACKS ====================

    /**
     * Handle B2C payment result callback
     * Called when B2C payment processing completes (success or failure)
     */
    @PostMapping("/b2c/result")
    public ResponseEntity<CallbackAcknowledgment> handleB2CResult(
            @RequestBody B2CPaymentResult paymentResult) {

        log.info("Received B2C payment result - ConversationID: {}, ResultCode: {}",
                paymentResult.result().conversationId(),
                paymentResult.result().resultCode());

        try {
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

        log.warn("Received B2C timeout - ConversationID: {}, ResultDesc: {}",
                timeoutCallback.conversationId(),
                timeoutCallback.resultDesc());

        try {
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

    /**
     * Handle B2B transfer result callback
     * Called when B2B transfer processing completes (success or failure)
     */
    @PostMapping("/b2b/result")
    public ResponseEntity<CallbackAcknowledgment> handleB2BResult(
            @RequestBody B2BTransferResult transferResult) {

        log.info("Received B2B transfer result - ConversationID: {}, ResultCode: {}",
                transferResult.result().conversationId(),
                transferResult.result().resultCode());

        try {
            B2BResult result = transferResult.result();

            if (result.isSuccessful()) {
                handleSuccessfulB2BTransfer(result);
            } else {
                handleFailedB2BTransfer(result);
            }

            return ResponseEntity.ok(CallbackAcknowledgment.success());

        } catch (Exception e) {
            log.error("Error processing B2B result callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
        }
    }

    /**
     * Handle B2B transfer timeout callback
     * Called when B2B transfer request times out
     */
    @PostMapping("/b2b/timeout")
    public ResponseEntity<CallbackAcknowledgment> handleB2BTimeout(
            @RequestBody B2BTimeoutCallback timeoutCallback) {

        log.warn("Received B2B timeout - ConversationID: {}, ResultDesc: {}",
                timeoutCallback.conversationId(),
                timeoutCallback.resultDesc());

        try {
            // Handle timeout - mark transfer as failed
            // TODO: Update transfer status to timeout
            // TODO: Notify relevant parties
            // TODO: Implement retry or manual intervention logic

            log.info("Processed B2B timeout for ConversationID: {}", timeoutCallback.conversationId());
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

        log.info("Received transaction status result - ConversationID: {}, ResultCode: {}",
                statusResult.result().conversationId(),
                statusResult.result().resultCode());

        try {
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

    /**
     * Handle account balance query result callback
     * Called with the account balance information
     */
    @PostMapping("/account-balance/result")
    public ResponseEntity<CallbackAcknowledgment> handleAccountBalanceResult(
            @RequestBody AccountBalanceResult balanceResult) {

        log.info("Received account balance result - ConversationID: {}, ResultCode: {}",
                balanceResult.result().conversationId(),
                balanceResult.result().resultCode());

        try {
            AccountBalanceResultData result = balanceResult.result();

            if (result.isSuccessful()) {
                handleAccountBalanceSuccess(result);
            } else {
                handleAccountBalanceError(result);
            }

            return ResponseEntity.ok(CallbackAcknowledgment.success());

        } catch (Exception e) {
            log.error("Error processing account balance result callback", e);
            return ResponseEntity.ok(CallbackAcknowledgment.error());
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

        log.info("Received reversal result - ConversationID: {}, ResultCode: {}",
                reversalResult.result().conversationId(),
                reversalResult.result().resultCode());

        try {
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

        log.warn("Received reversal timeout - ConversationID: {}, ResultDesc: {}",
                timeoutCallback.conversationId(),
                timeoutCallback.resultDesc());

        try {
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



    private void handleSuccessfulB2BTransfer(B2BResult result) {
        log.info("Processing successful B2B transfer - TransactionID: {}", result.transactionId());

        // Extract transfer details
        B2BResultParameters params = result.resultParameters();
        if (params != null) {
            String amount = params.getAmount();
            String debitBalance = params.getDebitAccountCurrentBalance();
            String receiverName = params.getReceiverPartyPublicName();

            log.info("B2B Transfer Details - Amount: {}, Debit Balance: {}, Receiver: {}",
                    amount, debitBalance, receiverName);

            // TODO: Update transfer status to completed
            // TODO: Update account balances
            // TODO: Send confirmations to both parties
        }
    }

    private void handleFailedB2BTransfer(B2BResult result) {
        log.warn("Processing failed B2B transfer - ResultDesc: {}", result.resultDesc());

        // TODO: Update transfer status to failed
        // TODO: Notify sender of failure
        // TODO: Implement retry logic if applicable
    }

    private void handleAccountBalanceSuccess(AccountBalanceResultData result) {
        log.info("Processing account balance success");

        // Extract balance details
        AccountBalanceResultParameters params = result.resultParameters();
        if (params != null) {
            String accountBalance = params.getAccountBalance();
            String workingFunds = params.getWorkingAccountAvailableFunds();
            String utilityFunds = params.getUtilityAccountAvailableFunds();

            log.info("Account Balance Details - Balance: {}, Working: {}, Utility: {}",
                    accountBalance, workingFunds, utilityFunds);

            // TODO: Update local balance records
            // TODO: Notify requesting party of balance
        }
    }

    private void handleAccountBalanceError(AccountBalanceResultData result) {
        log.error("Account balance query error - ResultDesc: {}", result.resultDesc());

        // TODO: Handle balance query error
        // TODO: Notify requesting party of error
    }

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