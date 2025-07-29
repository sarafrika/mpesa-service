package com.sarafrika.apps.mpesaservice.services;

import com.sarafrika.apps.mpesaservice.dtos.*;
import com.sarafrika.apps.mpesaservice.utils.enums.QRTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * M-Pesa Daraja API Service Interface
 * <p>
 * Provides access to all 8 core M-Pesa Daraja API functionalities:
 * <ol>
 * <li>STK Push (Lipa na M-Pesa Online)</li>
 * <li>Customer to Business (C2B)</li>
 * <li>Business to Customer (B2C)</li>
 * <li>Business to Business (B2B)</li>
 * <li>Transaction Status Query</li>
 * <li>Account Balance Query</li>
 * <li>Transaction Reversal</li>
 * <li>Dynamic QR Code Generation</li>
 * </ol>
 * </p>
 */
public interface MpesaDarajaService {

    // ==================== 1. STK PUSH (LIPA NA M-PESA ONLINE) ====================

    /**
     * Initiate STK Push payment request to customer's phone
     *
     * @param shortcodeUuid UUID of the configured shortcode
     * @param phoneNumber Customer phone number (254XXXXXXXXX format)
     * @param amount Transaction amount
     * @param accountReference Account reference for the transaction
     * @param transactionDesc Transaction description
     * @return STK Push response containing CheckoutRequestID and MerchantRequestID
     */
    MpesaApiResponse<StkPushResponse> initiateSTKPush(UUID shortcodeUuid, String phoneNumber, BigDecimal amount,
                                                      String accountReference, String transactionDesc);

    /**
     * Query STK Push transaction status
     *
     * @param shortcodeUuid UUID of the configured shortcode
     * @param checkoutRequestId Checkout request ID from STK Push initiation
     * @return STK Push status response
     */
    MpesaApiResponse<StkStatusResponse> queryStkPushStatus(UUID shortcodeUuid, String checkoutRequestId);

    // ==================== 2. CUSTOMER TO BUSINESS (C2B) ====================

    /**
     * Register C2B callback URLs for receiving payment notifications
     *
     * @param shortcodeUuid UUID of the configured shortcode
     * @param confirmationUrl URL for payment confirmations
     * @param validationUrl URL for payment validations
     * @return C2B registration response
     */
    MpesaApiResponse<C2BRegistrationResponse> registerC2BUrls(UUID shortcodeUuid, String confirmationUrl, String validationUrl);

    /**
     * Simulate C2B payment (for testing purposes)
     *
     * @param shortcodeUuid UUID of the configured shortcode
     * @param phoneNumber Customer phone number
     * @param amount Transaction amount
     * @param billRefNumber Bill reference number
     * @return C2B simulation response
     */
    MpesaApiResponse<C2BSimulationResponse> simulateC2BPayment(UUID shortcodeUuid, String phoneNumber,
                                                               BigDecimal amount, String billRefNumber);

    // ==================== 3. BUSINESS TO CUSTOMER (B2C) ====================

    /**
     * Send money from business to customer
     *
     * @param shortcodeUuid UUID of the configured shortcode
     * @param phoneNumber Recipient phone number
     * @param amount Amount to send
     * @param remarks Transaction remarks
     * @param occasion Transaction occasion
     * @return B2C payment response
     */
    MpesaApiResponse<B2CPaymentResponse> sendB2CPayment(UUID shortcodeUuid, String phoneNumber, BigDecimal amount,
                                                        String remarks, String occasion);

    // ==================== 4. BUSINESS TO BUSINESS (B2B) ====================

    /**
     * Transfer funds between business accounts
     *
     * @param senderShortcodeUuid UUID of sender's configured shortcode
     * @param receiverShortcode Receiver's shortcode number
     * @param amount Amount to transfer
     * @param remarks Transaction remarks
     * @param accountReference Account reference
     * @return B2B transfer response
     */
    MpesaApiResponse<B2BTransferResponse> transferB2B(UUID senderShortcodeUuid, String receiverShortcode,
                                                      BigDecimal amount, String remarks, String accountReference);

    // ==================== 5. TRANSACTION STATUS QUERY ====================

    /**
     * Query transaction status for any M-Pesa transaction
     *
     * @param shortcodeUuid UUID of the configured shortcode
     * @param transactionId M-Pesa transaction ID to query
     * @param remarks Query remarks
     * @return Transaction status response
     */
    MpesaApiResponse<TransactionStatusResponse> queryTransactionStatus(UUID shortcodeUuid, String transactionId, String remarks);

    // ==================== 6. ACCOUNT BALANCE QUERY ====================

    /**
     * Query M-Pesa account balance
     *
     * @param shortcodeUuid UUID of the configured shortcode
     * @param remarks Query remarks
     * @return Account balance response
     */
    MpesaApiResponse<AccountBalanceResponse> queryAccountBalance(UUID shortcodeUuid, String remarks);

    // ==================== 7. TRANSACTION REVERSAL ====================

    /**
     * Reverse a completed M-Pesa transaction
     *
     * @param shortcodeUuid UUID of the configured shortcode
     * @param transactionId M-Pesa transaction ID to reverse
     * @param amount Amount to reverse
     * @param remarks Reversal remarks
     * @return Transaction reversal response
     */
    MpesaApiResponse<TransactionReversalResponse> reverseTransaction(UUID shortcodeUuid, String transactionId,
                                                                     BigDecimal amount, String remarks);

    // ==================== 8. DYNAMIC QR CODE GENERATION ====================

    /**
     * Generate dynamic QR code for M-Pesa payments
     *
     * @param shortcodeUuid UUID of the configured shortcode
     * @param merchantName Business/merchant name
     * @param accountReference Account reference for the transaction
     * @param amount Transaction amount (optional - can be null for customer to enter)
     * @param transactionType Transaction type (BuyGoods, PayBill, SendMoney)
     * @param qrCodeSize QR code size in pixels
     * @return QR code generation response containing base64 encoded QR image
     */
    MpesaApiResponse<QRCodeResponse> generateDynamicQRCode(UUID shortcodeUuid, String merchantName, String accountReference,
                                                           BigDecimal amount, QRTransactionType transactionType, Integer qrCodeSize);

}