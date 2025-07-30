package com.sarafrika.apps.mpesaservice.clients;

import com.sarafrika.apps.mpesaservice.dtos.*;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

/**
 * M-Pesa Daraja API HTTP Interface Client
 * Uses Spring's HTTP Interface for declarative HTTP calls
 */
@HttpExchange
public interface MpesaDarajaHttpClient {

    // ==================== AUTHENTICATION ====================

    /**
     * Get OAuth access token
     */
    @GetExchange("/oauth/v1/generate")
    MpesaTokenResponse getAccessToken(
            @RequestParam("grant_type") String grantType,
            @RequestHeader("Authorization") String basicAuth
    );

    // ==================== STK PUSH ====================

    /**
     * Initiate STK Push payment
     */
    @PostExchange("/mpesa/stkpush/v1/processrequest")
    StkPushResponse initiateSTKPush(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );

    /**
     * Query STK Push status
     */
    @PostExchange("/mpesa/stkpushquery/v1/query")
    StkStatusResponse querySTKPushStatus(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );

    // ==================== C2B ====================

    /**
     * Register C2B URLs
     */
    @PostExchange("/mpesa/c2b/v1/registerurl")
    C2BRegistrationResponse registerC2BUrls(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );

    /**
     * Simulate C2B payment (sandbox only)
     */
    @PostExchange("/mpesa/c2b/v1/simulate")
    C2BSimulationResponse simulateC2BPayment(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );

    // ==================== B2C ====================

    /**
     * Send B2C payment
     */
    @PostExchange("/mpesa/b2c/v1/paymentrequest")
    B2CPaymentResponse sendB2CPayment(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );

    // ==================== B2B ====================

    /**
     * Transfer B2B funds
     */
    @PostExchange("/mpesa/b2b/v1/paymentrequest")
    B2BTransferResponse transferB2B(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );

    // ==================== TRANSACTION STATUS ====================

    /**
     * Query transaction status
     */
    @PostExchange("/mpesa/transactionstatus/v1/query")
    TransactionStatusResponse queryTransactionStatus(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );

    // ==================== ACCOUNT BALANCE ====================

    /**
     * Query account balance
     */
    @PostExchange("/mpesa/accountbalance/v1/query")
    AccountBalanceResponse queryAccountBalance(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );

    // ==================== TRANSACTION REVERSAL ====================

    /**
     * Reverse transaction
     */
    @PostExchange("/mpesa/reversal/v1/request")
    TransactionReversalResponse reverseTransaction(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );

    // ==================== QR CODE GENERATION ====================

    /**
     * Generate dynamic QR code
     */
    @PostExchange("/mpesa/qrcode/v1/generate")
    QRCodeResponse generateQRCode(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, Object> payload
    );
}