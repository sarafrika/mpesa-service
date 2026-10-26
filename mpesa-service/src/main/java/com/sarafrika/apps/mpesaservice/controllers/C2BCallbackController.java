package com.sarafrika.apps.mpesaservice.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.dtos.C2BConfirmationCallback;
import com.sarafrika.apps.mpesaservice.dtos.C2BValidationRequest;
import com.sarafrika.apps.mpesaservice.dtos.C2BValidationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Controller for handling C2B (Customer to Business) callbacks from M-Pesa Daraja API
 * <p>
 * This controller handles two types of C2B callbacks:
 * <ol>
 * <li> Validation - Called before payment is processed (can accept/reject) </li>
 * <li> Confirmation - Called after successful payment processing </li>
 * </ol>
 * </[>
 */
@RestController
@RequestMapping("/api/v1/callbacks/c2b")
@RequiredArgsConstructor
@Slf4j
public class C2BCallbackController {

    // TODO: Inject business logic services
    // private final PaymentValidationService paymentValidationService;
    // private final TransactionProcessingService transactionProcessingService;

    /**
     * Handle C2B validation callback
     * This endpoint is called BEFORE payment processing to validate the transaction
     * Return ResultCode 0 to accept, 1 to reject
     *
     * @param validationRequest The validation request from M-Pesa
     * @return Validation response (accept/reject)
     */
    @PostMapping("/validation")
    public ResponseEntity<C2BValidationResponse> handleC2BValidation(
            @RequestBody C2BValidationRequest validationRequest) {

        log.info("Received C2B validation request - TransID: {}, Amount: {}, BillRefNumber: {}, MSISDN: {}",
                validationRequest.transId(),
                validationRequest.transAmount(),
                validationRequest.billRefNumber(),
                validationRequest.msisdn());

        try {
            // Perform validation logic
            ValidationResult result = validatePayment(validationRequest);

            if (result.isValid()) {
                log.info("C2B payment validation passed for TransID: {}", validationRequest.transId());
                return ResponseEntity.ok(C2BValidationResponse.accept());
            } else {
                log.warn("C2B payment validation failed for TransID: {} - Reason: {}",
                        validationRequest.transId(), result.reason());
                return ResponseEntity.ok(C2BValidationResponse.reject(result.reason()));
            }

        } catch (Exception e) {
            log.error("Error during C2B validation for TransID: {}", validationRequest.transId(), e);
            return ResponseEntity.ok(C2BValidationResponse.reject("Internal validation error"));
        }
    }

    /**
     * Handle C2B confirmation callback
     * This endpoint is called AFTER successful payment processing
     * Used to update records and trigger business logic
     *
     * @param confirmationCallback The confirmation callback from M-Pesa
     * @return Acknowledgment response
     */
    @PostMapping("/confirmation")
    public ResponseEntity<C2BConfirmationAcknowledgment> handleC2BConfirmation(
            @RequestBody C2BConfirmationCallback confirmationCallback) {

        log.info("Received C2B confirmation - TransID: {}, Amount: {}, BillRefNumber: {}, Balance: {}",
                confirmationCallback.transId(),
                confirmationCallback.transAmount(),
                confirmationCallback.billRefNumber(),
                confirmationCallback.orgAccountBalance());

        try {
            // Process the confirmed payment
            processConfirmedPayment(confirmationCallback);

            log.info("Successfully processed C2B confirmation for TransID: {}", confirmationCallback.transId());

            return ResponseEntity.ok(new C2BConfirmationAcknowledgment(
                    "00000000",
                    "Success"
            ));

        } catch (Exception e) {
            log.error("Error processing C2B confirmation for TransID: {}", confirmationCallback.transId(), e);

            // Still return success to avoid M-Pesa retries
            return ResponseEntity.ok(new C2BConfirmationAcknowledgment(
                    "00000001",
                    "Internal processing error"
            ));
        }
    }

    /**
     * Validate incoming C2B payment
     * Implement your business validation logic here
     */
    private ValidationResult validatePayment(C2BValidationRequest request) {
        try {
            // Example validation rules

            // 1. Check minimum amount
            if (request.transAmount().compareTo(BigDecimal.ONE) < 0) {
                return ValidationResult.invalid("Amount too small. Minimum is 1 KES");
            }

            // 2. Check maximum amount
            if (request.transAmount().compareTo(BigDecimal.valueOf(100000)) > 0) {
                return ValidationResult.invalid("Amount too large. Maximum is 100,000 KES");
            }

            // 3. Validate account reference format
            if (request.billRefNumber() == null || request.billRefNumber().trim().isEmpty()) {
                return ValidationResult.invalid("Account reference is required");
            }

            // 4. Check if account reference exists
            // TODO: Implement account lookup logic
            // if (!accountService.accountExists(request.billRefNumber())) {
            //     return ValidationResult.invalid("Invalid account reference");
            // }

            // 5. Check for duplicate transactions
            // TODO: Implement duplicate check
            // if (transactionService.isDuplicate(request.transId())) {
            //     return ValidationResult.invalid("Duplicate transaction");
            // }

            // 6. Customer-specific validations
            // TODO: Implement customer validation
            // if (!customerService.isActive(request.msisdn())) {
            //     return ValidationResult.invalid("Customer account is not active");
            // }

            return ValidationResult.valid();

        } catch (Exception e) {
            log.error("Error during payment validation", e);
            return ValidationResult.invalid("Validation service temporarily unavailable");
        }
    }

    /**
     * Process confirmed C2B payment
     * Implement your business logic here
     */
    private void processConfirmedPayment(C2BConfirmationCallback callback) {
        log.info("Processing confirmed C2B payment for TransID: {}", callback.transId());

        try {
            // 1. Save transaction to database
            // TODO: Save transaction record
            // Transaction transaction = Transaction.builder()
            //     .transactionId(callback.transId())
            //     .amount(callback.transAmount())
            //     .phoneNumber(callback.msisdn())
            //     .accountReference(callback.billRefNumber())
            //     .transactionTime(parseTransactionTime(callback.transTime()))
            //     .status(TransactionStatus.COMPLETED)
            //     .build();
            // transactionRepository.save(transaction);

            // 2. Update customer account/balance
            // TODO: Credit customer account
            // accountService.creditAccount(callback.billRefNumber(), callback.transAmount());

            // 3. Send confirmation notification
            // TODO: Send SMS/email confirmation
            // notificationService.sendPaymentConfirmation(
            //     callback.msisdn(),
            //     callback.transAmount(),
            //     callback.transId()
            // );

            // 4. Trigger any business workflows
            // TODO: Trigger order processing, service activation, etc.
            // workflowService.triggerPaymentWorkflow(callback.billRefNumber(), callback.transAmount());

            log.info("Successfully processed confirmed payment for TransID: {}", callback.transId());

        } catch (Exception e) {
            log.error("Error processing confirmed payment for TransID: {}", callback.transId(), e);
            // TODO: Implement retry logic or manual intervention queue
            throw e;
        }
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Validation result holder
     */
    private record ValidationResult(boolean isValid, String reason) {

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    /**
     * C2B confirmation acknowledgment response
     */
    public record C2BConfirmationAcknowledgment(
            @JsonProperty("ResultCode")
            String resultCode,

            @JsonProperty("ResultDesc")
            String resultDesc
    ) {}
}