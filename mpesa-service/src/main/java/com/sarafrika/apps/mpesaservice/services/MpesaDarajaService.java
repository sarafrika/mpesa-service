package com.sarafrika.apps.mpesaservice.services;

import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;

import com.sarafrika.apps.mpesaservice.dtos.*;
import com.sarafrika.apps.mpesaservice.utils.enums.QRTransactionType;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiFunction;

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
    default MpesaApiResponse<C2BRegistrationResponse> registerC2BUrls(UUID shortcodeUuid, String confirmationUrl,
                                                                      String validationUrl) {
        return registerC2BUrls(shortcodeUuid, confirmationUrl, validationUrl, "Completed");
    }

    /** As above, with Safaricom's ResponseType: Completed or Cancelled when validation is unreachable. */
    MpesaApiResponse<C2BRegistrationResponse> registerC2BUrls(UUID shortcodeUuid, String confirmationUrl,
                                                              String validationUrl, String responseType);

    /** Forgets a cached Daraja token, e.g. after the shortcode's credentials were rotated. */
    void evictAccessToken(UUID shortcodeUuid);

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

    /**
     * B2C v3 payout from a shortcode; {@code originatorConversationId} is the caller's id for the
     * request and comes back on Safaricom's result callback.
     */
    MpesaApiResponse<B2CPaymentResponse> sendB2CPayout(MpesaShortCode shortcode, String originatorConversationId,
                                                       String phoneNumber, BigDecimal amount, String remarks,
                                                       String occasion);

    // ==================== 4. BUSINESS TO BUSINESS (B2B) ====================

    /** B2B payment (BusinessPayBill or BusinessBuyGoods) from a shortcode; Safaricom posts the outcome to the
     * B2B result URL, or the B2B timeout URL when it could not process the request in time. */
    MpesaApiResponse<B2BTransferResponse> sendB2BPayment(MpesaShortCode shortcode, B2BPaymentRequest request);

    // ==================== FLAGGED PRODUCTS ====================

    /** Calls a flagged Daraja product (services.daraja.products) with the shortcode's token; the caller builds
     * the request and picks the endpoint. No token is AUTH_FAILED with nothing sent. */
    <T> MpesaApiResponse<T> callProduct(MpesaShortCode shortcode, String operation,
                                        BiFunction<MpesaDarajaHttpClient, String, T> call);

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

    /** Transaction Status for a receipt, or (when the receipt is null) a request's original conversation id;
     * Safaricom posts the answer to the transaction-status result URL. */
    MpesaApiResponse<TransactionStatusResponse> queryTransactionStatus(MpesaShortCode shortcode, String transactionId,
                                                                       String originalConversationId, String remarks);

    // ==================== 6. ACCOUNT BALANCE QUERY ====================

    /** Account Balance for a shortcode; Safaricom posts the balances to the account-balance result URL, or
     * the account-balance timeout URL when it could not process the query in time. */
    MpesaApiResponse<AccountBalanceResponse> queryAccountBalance(MpesaShortCode shortcode, String remarks);

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

    /** Results go to the reversal callbacks; a failure to prepare the request is NOT_SENT (final). */
    MpesaApiResponse<TransactionReversalResponse> reverseTransaction(MpesaShortCode shortcode, String transactionId,
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

    // ==================== 9. PULL TRANSACTIONS ====================

    /** One-time Pull Transactions registration of a shortcode; ResponseStatus 1000 registered, 1001 already
     * registered (developer.safaricom.co.ke/apis/PullTransaction). The reply is Safaricom's JSON as a map. */
    MpesaApiResponse<java.util.Map<String, Object>> registerPullTransactions(MpesaShortCode shortcode,
                                                                          String nominatedNumber, String callbackUrl);

    /** C2B transactions between start and end (East Africa Time, within the last 48 hours), from row offset;
     * ResponseCode 1000 has rows in Response, 1001 none. */
    MpesaApiResponse<java.util.Map<String, Object>> queryPullTransactions(MpesaShortCode shortcode,
                                                                       java.time.LocalDateTime start,
                                                                       java.time.LocalDateTime end, int offset);
}
