package com.sarafrika.apps.mpesaservice.services.impl;

import org.springframework.beans.factory.annotation.Value;

import com.sarafrika.apps.mpesaservice.services.payout.SecurityCredentialService;

import com.sarafrika.apps.mpesaservice.clients.DarajaHttpException;
import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.dtos.*;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.MpesaIncomingPaymentService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import com.sarafrika.apps.mpesaservice.utils.enums.QRTransactionType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MpesaDarajaServiceImpl implements MpesaDarajaService {

    private final MpesaShortCodeRepository shortCodeRepository;
    private final MpesaIncomingPaymentService incomingPaymentService;
    private final MpesaDarajaHttpClient sandboxHttpClient;
    private final MpesaDarajaHttpClient productionHttpClient;
    private final SecurityCredentialService securityCredentialService;
    private final String callbackBaseUrl;

    // Cache for access tokens to avoid frequent authentication calls
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long TOKEN_CACHE_DURATION_MINUTES = 55; // M-Pesa tokens expire in 1 hour

    /**
     * Constructor with proper @Qualifier annotations
     */
    public MpesaDarajaServiceImpl(
            MpesaShortCodeRepository shortCodeRepository,
            MpesaIncomingPaymentService incomingPaymentService,
            @Qualifier("sandboxHttpClient") MpesaDarajaHttpClient sandboxHttpClient,
            @Qualifier("productionHttpClient") MpesaDarajaHttpClient productionHttpClient,
            SecurityCredentialService securityCredentialService,
            @Value("${mpesa.callback.base-url:}") String callbackBaseUrl) {

        this.securityCredentialService = securityCredentialService;
        this.callbackBaseUrl = callbackBaseUrl;
        this.shortCodeRepository = shortCodeRepository;
        this.incomingPaymentService = incomingPaymentService;
        this.sandboxHttpClient = sandboxHttpClient;
        this.productionHttpClient = productionHttpClient;
    }

    /**
     * Cached token holder
     */
    private record CachedToken(String token, LocalDateTime expiryTime) {
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiryTime);
        }
    }

    // ==================== 1. STK PUSH IMPLEMENTATION ====================

    @Override
    public MpesaApiResponse<StkPushResponse> initiateSTKPush(UUID shortcodeUuid, String phoneNumber,
                                                             BigDecimal amount, String accountReference,
                                                             String transactionDesc) {
        log.info("Initiating STK Push for shortcode: {}, phone: {}, amount: {}", shortcodeUuid, phoneNumber, amount);

        long startTime = System.currentTimeMillis();

        try {
            // Get shortcode configuration
            MpesaShortCode shortcode = getShortcodeOrThrow(shortcodeUuid);

            // A suspended (or never activated) shortcode takes no new payments.
            if (!Boolean.TRUE.equals(shortcode.getIsActive())) {
                return MpesaApiResponse.error("SHORTCODE_SUSPENDED", "Shortcode " + shortcode.getShortcode()
                        + " is suspended; STK push is refused", HttpStatus.SERVICE_UNAVAILABLE.value());
            }

            // Validate STK Push capability
            if (!isSTKPushEnabled(shortcode)) {
                return MpesaApiResponse.error("INVALID_CONFIG",
                        "STK Push not enabled for this shortcode", HttpStatus.BAD_REQUEST.value());
            }

            // Get access token
            String accessToken = getAccessToken(shortcode);
            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED",
                        "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }

            // Build request payload
            Map<String, Object> payload = buildSTKPushPayload(shortcode, phoneNumber, amount,
                    accountReference, transactionDesc);

            // Make API call using HTTP Interface
            MpesaDarajaHttpClient httpClient = getHttpClient(shortcode.getEnvironment());
            StkPushResponse response = httpClient.initiateSTKPush("Bearer " + accessToken, payload);

            // Persist a PENDING record so the callback can later reconcile it and
            // consuming systems can poll payment status by checkout request id.
            persistPendingStkPush(shortcodeUuid, phoneNumber, amount, accountReference, transactionDesc, response);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("STK Push initiated successfully for shortcode: {}, processing time: {}ms",
                    shortcodeUuid, processingTime);

            return MpesaApiResponse.success(response, HttpStatus.OK.value(), processingTime);

        } catch (Exception e) {
            return handleException(e, startTime, "STK Push initiation");
        }
    }

    @Override
    public MpesaApiResponse<StkStatusResponse> queryStkPushStatus(UUID shortcodeUuid, String checkoutRequestId) {
        log.info("Querying STK Push status for shortcode: {}, checkoutRequestId: {}", shortcodeUuid, checkoutRequestId);

        long startTime = System.currentTimeMillis();

        try {
            MpesaShortCode shortcode = getShortcodeOrThrow(shortcodeUuid);
            String accessToken = getAccessToken(shortcode);

            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED",
                        "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }

            Map<String, Object> payload = buildSTKStatusPayload(shortcode, checkoutRequestId);

            MpesaDarajaHttpClient httpClient = getHttpClient(shortcode.getEnvironment());
            StkStatusResponse response = httpClient.querySTKPushStatus("Bearer " + accessToken, payload);

            long processingTime = System.currentTimeMillis() - startTime;
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), processingTime);

        } catch (Exception e) {
            return handleException(e, startTime, "STK Push status query");
        }
    }

    // ==================== 2. C2B IMPLEMENTATION ====================

    @Override
    public MpesaApiResponse<C2BRegistrationResponse> registerC2BUrls(UUID shortcodeUuid, String confirmationUrl,
                                                                     String validationUrl, String responseType) {
        log.info("Registering C2B URLs for shortcode: {}", shortcodeUuid);

        long startTime = System.currentTimeMillis();

        try {
            MpesaShortCode shortcode = getShortcodeOrThrow(shortcodeUuid);
            String accessToken = getAccessToken(shortcode);

            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED",
                        "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }

            Map<String, Object> payload = buildC2BRegistrationPayload(shortcode, confirmationUrl, validationUrl,
                    responseType);

            MpesaDarajaHttpClient httpClient = getHttpClient(shortcode.getEnvironment());
            C2BRegistrationResponse response = httpClient.registerC2BUrls("Bearer " + accessToken, payload);

            long processingTime = System.currentTimeMillis() - startTime;
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), processingTime);

        } catch (Exception e) {
            return handleException(e, startTime, "C2B URL registration");
        }
    }

    @Override
    public MpesaApiResponse<C2BSimulationResponse> simulateC2BPayment(UUID shortcodeUuid, String phoneNumber,
                                                                      BigDecimal amount, String billRefNumber) {
        log.info("Simulating C2B payment for shortcode: {}, phone: {}, amount: {}", shortcodeUuid, phoneNumber, amount);

        long startTime = System.currentTimeMillis();

        try {
            MpesaShortCode shortcode = getShortcodeOrThrow(shortcodeUuid);

            // C2B simulation only available in sandbox
            if (shortcode.getEnvironment() != Environment.SANDBOX) {
                return MpesaApiResponse.error("INVALID_ENVIRONMENT",
                        "C2B simulation only available in sandbox environment", HttpStatus.BAD_REQUEST.value());
            }

            String accessToken = getAccessToken(shortcode);
            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED",
                        "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }

            Map<String, Object> payload = buildC2BSimulationPayload(shortcode, phoneNumber, amount, billRefNumber);

            MpesaDarajaHttpClient httpClient = getHttpClient(shortcode.getEnvironment());
            C2BSimulationResponse response = httpClient.simulateC2BPayment("Bearer " + accessToken, payload);

            long processingTime = System.currentTimeMillis() - startTime;
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), processingTime);

        } catch (Exception e) {
            return handleException(e, startTime, "C2B payment simulation");
        }
    }

    // ==================== 3. B2C IMPLEMENTATION ====================

    @Override
    public MpesaApiResponse<B2CPaymentResponse> sendB2CPayment(UUID shortcodeUuid, String phoneNumber,
                                                               BigDecimal amount, String remarks, String occasion) {
        log.info("Sending B2C payment for shortcode: {}, phone: {}, amount: {}", shortcodeUuid, phoneNumber, amount);

        long startTime = System.currentTimeMillis();

        try {
            MpesaShortCode shortcode = getShortcodeOrThrow(shortcodeUuid);
            String accessToken = getAccessToken(shortcode);

            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED",
                        "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }

            Map<String, Object> payload = buildB2CPaymentPayload(shortcode, phoneNumber, amount, remarks, occasion);

            MpesaDarajaHttpClient httpClient = getHttpClient(shortcode.getEnvironment());
            B2CPaymentResponse response = httpClient.sendB2CPayment("Bearer " + accessToken, payload);

            long processingTime = System.currentTimeMillis() - startTime;
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), processingTime);

        } catch (Exception e) {
            return handleException(e, startTime, "B2C payment");
        }
    }

    @Override
    public MpesaApiResponse<B2CPaymentResponse> sendB2CPayout(MpesaShortCode shortcode, String originatorConversationId,
                                                              String phoneNumber, BigDecimal amount, String remarks,
                                                              String occasion) {
        long startTime = System.currentTimeMillis();
        String accessToken;
        Map<String, Object> payload = new HashMap<>();
        try {
            accessToken = getAccessToken(shortcode);
            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED", "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }
            payload.put("OriginatorConversationID", originatorConversationId);
            payload.put("InitiatorName", shortcode.getInitiatorName());
            payload.put("SecurityCredential", securityCredentialService.credentialFor(shortcode));
            payload.put("CommandID", "BusinessPayment");
            payload.put("Amount", amount.setScale(0, java.math.RoundingMode.UNNECESSARY).toPlainString());
            payload.put("PartyA", shortcode.getShortcode());
            payload.put("PartyB", phoneNumber);
            payload.put("Remarks", remarks != null && !remarks.isBlank() ? remarks : "Payout");
            payload.put("QueueTimeOutURL", b2cUrl(shortcode.getB2cTimeoutUrl(), "/b2c/timeout"));
            payload.put("ResultURL", b2cUrl(shortcode.getB2cResultUrl(), "/b2c/result"));
            payload.put("Occasion", occasion != null && !occasion.isBlank() ? occasion : "Payout");
        } catch (Exception e) {
            // Nothing reached Safaricom, so this refusal is final.
            log.error("B2C payout {} not sent: {}", originatorConversationId, e.toString());
            return MpesaApiResponse.error(MpesaErrorResponse.NOT_SENT, "The payout could not be prepared: "
                    + e.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY.value());
        }
        try {
            B2CPaymentResponse response = getHttpClient(shortcode.getEnvironment())
                    .sendB2CPaymentV3("Bearer " + accessToken, payload);
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return handleException(e, startTime, "B2C payout");
        }
    }

    private String b2cUrl(String configured, String path) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return callbackUrl(path);
    }

    private String callbackUrl(String path) {
        return callbackBaseUrl.replaceAll("/+$", "") + path;
    }

    // ==================== 4. B2B IMPLEMENTATION ====================

    @Override
    public MpesaApiResponse<B2BTransferResponse> sendB2BPayment(MpesaShortCode shortcode, B2BPaymentRequest request) {
        long startTime = System.currentTimeMillis();
        String accessToken;
        Map<String, Object> payload;
        try {
            accessToken = getAccessToken(shortcode);
            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED", "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }
            payload = buildB2BPaymentPayload(shortcode, request);
        } catch (Exception e) {
            // Nothing reached Safaricom, so this refusal is final.
            log.error("B2B payment from {} not sent: {}", shortcode.getShortcode(), e.toString());
            return MpesaApiResponse.error(MpesaErrorResponse.NOT_SENT, "The B2B payment could not be prepared: "
                    + e.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY.value());
        }
        try {
            B2BTransferResponse response = getHttpClient(shortcode.getEnvironment())
                    .transferB2B("Bearer " + accessToken, payload);
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return handleException(e, startTime, "B2B payment");
        }
    }

    // ==================== FLAGGED PRODUCTS ====================

    @Override
    public <T> MpesaApiResponse<T> callProduct(MpesaShortCode shortcode, String operation,
                                               java.util.function.BiFunction<MpesaDarajaHttpClient, String, T> call) {
        long startTime = System.currentTimeMillis();
        String accessToken = getAccessToken(shortcode);
        if (accessToken == null) {
            return MpesaApiResponse.error("AUTH_FAILED", "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
        }
        try {
            T response = call.apply(getHttpClient(shortcode.getEnvironment()), "Bearer " + accessToken);
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return handleException(e, startTime, operation);
        }
    }

    // ==================== 5. TRANSACTION STATUS IMPLEMENTATION ====================

    @Override
    public MpesaApiResponse<TransactionStatusResponse> queryTransactionStatus(UUID shortcodeUuid,
                                                                              String transactionId, String remarks) {
        log.info("Querying transaction status for shortcode: {}, transactionId: {}", shortcodeUuid, transactionId);

        long startTime = System.currentTimeMillis();

        try {
            MpesaShortCode shortcode = getShortcodeOrThrow(shortcodeUuid);
            String accessToken = getAccessToken(shortcode);

            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED",
                        "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }

            Map<String, Object> payload = buildTransactionStatusPayload(shortcode, transactionId, remarks);

            MpesaDarajaHttpClient httpClient = getHttpClient(shortcode.getEnvironment());
            TransactionStatusResponse response = httpClient.queryTransactionStatus("Bearer " + accessToken, payload);

            long processingTime = System.currentTimeMillis() - startTime;
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), processingTime);

        } catch (Exception e) {
            return handleException(e, startTime, "Transaction status query");
        }
    }

    @Override
    public MpesaApiResponse<TransactionStatusResponse> queryTransactionStatus(MpesaShortCode shortcode,
                                                                              String transactionId,
                                                                              String originalConversationId,
                                                                              String remarks) {
        long startTime = System.currentTimeMillis();
        try {
            String accessToken = getAccessToken(shortcode);
            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED",
                        "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }
            Map<String, Object> payload = buildTransactionStatusPayload(shortcode, transactionId, remarks);
            // Lets Safaricom find a B2C/B2B request that never got a receipt (unverified outside the portal).
            if (transactionId == null && originalConversationId != null) {
                payload.remove("TransactionID");
                payload.put("OriginalConversationID", originalConversationId);
            }
            TransactionStatusResponse response = getHttpClient(shortcode.getEnvironment())
                    .queryTransactionStatus("Bearer " + accessToken, payload);
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return handleException(e, startTime, "Transaction status query");
        }
    }

    // ==================== 6. ACCOUNT BALANCE IMPLEMENTATION ====================

    @Override
    public MpesaApiResponse<AccountBalanceResponse> queryAccountBalance(MpesaShortCode shortcode, String remarks) {
        long startTime = System.currentTimeMillis();
        String accessToken;
        Map<String, Object> payload;
        try {
            accessToken = getAccessToken(shortcode);
            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED", "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }
            payload = buildAccountBalancePayload(shortcode, remarks);
        } catch (Exception e) {
            log.error("Account balance query for {} not sent: {}", shortcode.getShortcode(), e.toString());
            return MpesaApiResponse.error(MpesaErrorResponse.NOT_SENT, "The balance query could not be prepared: "
                    + e.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY.value());
        }
        try {
            AccountBalanceResponse response = getHttpClient(shortcode.getEnvironment())
                    .queryAccountBalance("Bearer " + accessToken, payload);
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return handleException(e, startTime, "Account balance query");
        }
    }

    // ==================== 7. TRANSACTION REVERSAL IMPLEMENTATION ====================

    @Override
    public MpesaApiResponse<TransactionReversalResponse> reverseTransaction(UUID shortcodeUuid, String transactionId,
                                                                            BigDecimal amount, String remarks) {
        try {
            return reverseTransaction(getShortcodeOrThrow(shortcodeUuid), transactionId, amount, remarks);
        } catch (Exception e) {
            return handleException(e, System.currentTimeMillis(), "Transaction reversal");
        }
    }

    @Override
    public MpesaApiResponse<TransactionReversalResponse> reverseTransaction(MpesaShortCode shortcode,
                                                                            String transactionId, BigDecimal amount,
                                                                            String remarks) {
        log.info("Reversing transaction {} on shortcode {}, amount {}", transactionId, shortcode.getShortcode(), amount);
        long startTime = System.currentTimeMillis();
        String accessToken;
        Map<String, Object> payload;
        try {
            accessToken = getAccessToken(shortcode);
            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED", "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }
            payload = buildTransactionReversalPayload(shortcode, transactionId, amount, remarks);
        } catch (Exception e) {
            // Nothing reached Safaricom, so this refusal is final.
            log.error("Reversal of {} not sent: {}", transactionId, e.toString());
            return MpesaApiResponse.error(MpesaErrorResponse.NOT_SENT, "The reversal could not be prepared: "
                    + e.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY.value());
        }
        try {
            TransactionReversalResponse response = getHttpClient(shortcode.getEnvironment())
                    .reverseTransaction("Bearer " + accessToken, payload);
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return handleException(e, startTime, "Transaction reversal");
        }
    }

    // ==================== 8. QR CODE IMPLEMENTATION ====================

    @Override
    public MpesaApiResponse<QRCodeResponse> generateDynamicQRCode(UUID shortcodeUuid, String merchantName,
                                                                  String accountReference, BigDecimal amount,
                                                                  QRTransactionType transactionType, Integer qrCodeSize) {
        log.info("Generating QR code for shortcode: {}, type: {}, amount: {}",
                shortcodeUuid, transactionType, amount);

        long startTime = System.currentTimeMillis();

        try {
            MpesaShortCode shortcode = getShortcodeOrThrow(shortcodeUuid);
            String accessToken = getAccessToken(shortcode);

            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED",
                        "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }

            Map<String, Object> payload = buildQRCodePayload(shortcode, merchantName, accountReference,
                    amount, transactionType, qrCodeSize);

            MpesaDarajaHttpClient httpClient = getHttpClient(shortcode.getEnvironment());
            QRCodeResponse response = httpClient.generateQRCode("Bearer " + accessToken, payload);

            long processingTime = System.currentTimeMillis() - startTime;
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), processingTime);

        } catch (Exception e) {
            return handleException(e, startTime, "QR code generation");
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Persist a PENDING incoming payment for an initiated STK Push so the asynchronous
     * callback can reconcile it and consumers can poll its status. The record is keyed on
     * the checkout request id; the transaction id is seeded with the checkout request id
     * (a unique, non-null placeholder) and later replaced with the real M-Pesa receipt when
     * the successful callback arrives.
     */
    private void persistPendingStkPush(UUID shortcodeUuid, String phoneNumber, BigDecimal amount,
                                       String accountReference, String transactionDesc, StkPushResponse response) {
        if (response == null || response.checkoutRequestId() == null) {
            log.warn("Skipping STK Push persistence for shortcode {}: missing checkout request id", shortcodeUuid);
            return;
        }
        try {
            MpesaIncomingPayment payment = new MpesaIncomingPayment();
            payment.setShortcodeUuid(shortcodeUuid);
            payment.setPaymentType(IncomingPaymentType.STK_PUSH);
            payment.setPhoneNumber(phoneNumber);
            payment.setAmount(amount);
            payment.setAccountReference(accountReference);
            payment.setTransactionDesc(transactionDesc);
            payment.setCheckoutRequestId(response.checkoutRequestId());
            payment.setMerchantRequestId(response.merchantRequestId());
            payment.setTransactionId(response.checkoutRequestId());
            payment.setStatus(IncomingPaymentStatus.PENDING);
            payment.setClientId(callingClientId());
            incomingPaymentService.create(payment);
            log.debug("Persisted PENDING STK Push payment for checkoutRequestId: {}", response.checkoutRequestId());
        } catch (Exception e) {
            // Never fail the STK Push response because of a persistence issue; log for reconciliation.
            log.error("Failed to persist PENDING STK Push payment for shortcode {}: {}",
                    shortcodeUuid, e.getMessage(), e);
        }
    }

    /** The product (Keycloak azp) whose token asked for this, so its payment events reach it. */
    private static String callingClientId() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getClaimAsString("azp");
        }
        return null;
    }

    @Override
    public void evictAccessToken(UUID shortcodeUuid) {
        tokenCache.remove(shortcodeUuid.toString());
    }

    private MpesaShortCode getShortcodeOrThrow(UUID shortcodeUuid) {
        return shortCodeRepository.findByUuid(shortcodeUuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found: " + shortcodeUuid));
    }

    private MpesaDarajaHttpClient getHttpClient(Environment environment) {
        return environment == Environment.PRODUCTION ? productionHttpClient : sandboxHttpClient;
    }

    private boolean isSTKPushEnabled(MpesaShortCode shortcode) {
        return shortcode.getPasskey() != null && !shortcode.getPasskey().trim().isEmpty();
    }

    private String getAccessToken(MpesaShortCode shortcode) {
        String cacheKey = shortcode.getUuid().toString();

        // Check cache first
        CachedToken cachedToken = tokenCache.get(cacheKey);
        if (cachedToken != null && !cachedToken.isExpired()) {
            log.debug("Using cached token for shortcode: {}", shortcode.getUuid());
            return cachedToken.token();
        }

        try {
            String credentials = shortcode.getConsumerKey() + ":" + shortcode.getConsumerSecret();
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            MpesaDarajaHttpClient httpClient = getHttpClient(shortcode.getEnvironment());
            MpesaTokenResponse response = httpClient.getAccessToken("client_credentials", "Basic " + encodedCredentials);

            if (response != null && response.isValid()) {
                // Cache the token
                LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(TOKEN_CACHE_DURATION_MINUTES);
                tokenCache.put(cacheKey, new CachedToken(response.accessToken(), expiryTime));

                log.debug("Retrieved and cached new token for shortcode: {}", shortcode.getUuid());
                return response.accessToken();
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to get access token for shortcode: {}", shortcode.getUuid(), e);
            // Remove invalid cached token
            tokenCache.remove(cacheKey);
            return null;
        }
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    private String generatePassword(MpesaShortCode shortcode) {
        String timestamp = getCurrentTimestamp();
        String password = shortcode.getShortcode() + shortcode.getPasskey() + timestamp;
        return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
    }

    private <T> MpesaApiResponse<T> handleException(Exception e, long startTime, String operation) {
        long processingTime = System.currentTimeMillis() - startTime;

        log.error("Error during {}: {}", operation, e.getMessage(), e);

        // Extract meaningful error messages from different exception types
        String errorMessage = e.getMessage();
        String errorCode = "INTERNAL_ERROR";
        int httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();

        // Handle specific exception types
        switch (e) {
            case DarajaHttpException daraja when daraja.errorCode() != null -> {
                errorCode = daraja.errorCode();
                errorMessage = daraja.errorMessage() != null ? daraja.errorMessage() : errorMessage;
                httpStatus = daraja.status();
            }
            case DarajaHttpException daraja -> {
                errorCode = daraja.status() >= 500 ? "SERVER_ERROR" : "CLIENT_ERROR";
                httpStatus = daraja.status() >= 500 ? HttpStatus.BAD_GATEWAY.value() : daraja.status();
            }
            case ResourceAccessException network -> {
                errorCode = MpesaErrorResponse.NETWORK_ERROR;
                httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
            }
            case IllegalArgumentException illegalArgumentException -> {
                errorCode = "INVALID_ARGUMENT";
                httpStatus = HttpStatus.BAD_REQUEST.value();
            }
            case RuntimeException runtimeException when e.getMessage() != null && e.getMessage().contains("Client error") -> {
                errorCode = "CLIENT_ERROR";
                httpStatus = HttpStatus.BAD_REQUEST.value();
            }
            case RuntimeException runtimeException when e.getMessage() != null && e.getMessage().contains("Server error") -> {
                errorCode = "SERVER_ERROR";
                httpStatus = HttpStatus.BAD_GATEWAY.value();
            }
            default -> {
            }
        }

        return MpesaApiResponse.error(errorCode, errorMessage, httpStatus, processingTime);
    }

    /**
     * Create error response from exception with processing time
     */
    public static <T> MpesaApiResponse<T> error(String errorCode, String errorMessage, int httpStatus, long processingTimeMs) {
        MpesaErrorResponse errorResponse = new MpesaErrorResponse(
                errorCode,
                errorMessage,
                null,
                LocalDateTime.now()
        );
        return new MpesaApiResponse<>(false, null, errorResponse, LocalDateTime.now(), httpStatus, processingTimeMs);
    }

    // ==================== 9. PULL TRANSACTIONS IMPLEMENTATION ====================

    private static final DateTimeFormatter PULL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public MpesaApiResponse<Map<String, Object>> registerPullTransactions(MpesaShortCode shortcode,
                                                                         String nominatedNumber, String callbackUrl) {
        long startTime = System.currentTimeMillis();
        try {
            String accessToken = getAccessToken(shortcode);
            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED", "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("ShortCode", shortcode.getShortcode());
            payload.put("RequestType", "Pull");
            payload.put("NominatedNumber", nominatedNumber);
            payload.put("CallBackURL", callbackUrl);
            Map<String, Object> response = getHttpClient(shortcode.getEnvironment())
                    .registerPullTransactions("Bearer " + accessToken, payload);
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return handleException(e, startTime, "Pull Transactions registration");
        }
    }

    @Override
    public MpesaApiResponse<Map<String, Object>> queryPullTransactions(MpesaShortCode shortcode, LocalDateTime start,
                                                                      LocalDateTime end, int offset) {
        long startTime = System.currentTimeMillis();
        try {
            String accessToken = getAccessToken(shortcode);
            if (accessToken == null) {
                return MpesaApiResponse.error("AUTH_FAILED", "Failed to obtain access token", HttpStatus.UNAUTHORIZED.value());
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("ShortCode", shortcode.getShortcode());
            payload.put("StartDate", start.format(PULL_TIME));
            payload.put("EndDate", end.format(PULL_TIME));
            payload.put("OffSetValue", String.valueOf(offset));
            Map<String, Object> response = getHttpClient(shortcode.getEnvironment())
                    .queryPullTransactions("Bearer " + accessToken, payload);
            return MpesaApiResponse.success(response, HttpStatus.OK.value(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return handleException(e, startTime, "Pull Transactions query");
        }
    }

    // ==================== PAYLOAD BUILDERS ====================

    private Map<String, Object> buildSTKPushPayload(MpesaShortCode shortcode, String phoneNumber,
                                                    BigDecimal amount, String accountReference, String transactionDesc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("BusinessShortCode", shortcode.getShortcode());
        payload.put("Password", generatePassword(shortcode));
        payload.put("Timestamp", getCurrentTimestamp());
        payload.put("TransactionType", "CustomerPayBillOnline");
        payload.put("Amount", amount);
        payload.put("PartyA", phoneNumber);
        payload.put("PartyB", shortcode.getShortcode());
        payload.put("PhoneNumber", phoneNumber);
        payload.put("CallBackURL", shortcode.getCallbackUrl());
        payload.put("AccountReference", accountReference != null ? accountReference : shortcode.getAccountReference());
        payload.put("TransactionDesc", transactionDesc != null ? transactionDesc : shortcode.getTransactionDesc());
        return payload;
    }

    private Map<String, Object> buildSTKStatusPayload(MpesaShortCode shortcode, String checkoutRequestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("BusinessShortCode", shortcode.getShortcode());
        payload.put("Password", generatePassword(shortcode));
        payload.put("Timestamp", getCurrentTimestamp());
        payload.put("CheckoutRequestID", checkoutRequestId);
        return payload;
    }

    private Map<String, Object> buildC2BRegistrationPayload(MpesaShortCode shortcode, String confirmationUrl,
                                                            String validationUrl, String responseType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ShortCode", shortcode.getShortcode());
        payload.put("ResponseType", "Cancelled".equals(responseType) ? "Cancelled" : "Completed");
        payload.put("ConfirmationURL", confirmationUrl);
        payload.put("ValidationURL", validationUrl);
        return payload;
    }

    private Map<String, Object> buildC2BSimulationPayload(MpesaShortCode shortcode, String phoneNumber,
                                                          BigDecimal amount, String billRefNumber) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ShortCode", shortcode.getShortcode());
        payload.put("CommandID", "CustomerPayBillOnline");
        payload.put("Amount", amount);
        payload.put("Msisdn", phoneNumber);
        payload.put("BillRefNumber", billRefNumber != null ? billRefNumber : shortcode.getAccountReference());
        return payload;
    }

    private Map<String, Object> buildB2CPaymentPayload(MpesaShortCode shortcode, String phoneNumber,
                                                       BigDecimal amount, String remarks, String occasion) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("InitiatorName", shortcode.getInitiatorName());
        payload.put("SecurityCredential", securityCredentialService.credentialFor(shortcode));
        payload.put("CommandID", "BusinessPayment");
        payload.put("Amount", amount);
        payload.put("PartyA", shortcode.getShortcode());
        payload.put("PartyB", phoneNumber);
        payload.put("Remarks", remarks != null ? remarks : "B2C Payment");
        payload.put("QueueTimeOutURL", shortcode.getCallbackUrl());
        payload.put("ResultURL", shortcode.getCallbackUrl());
        payload.put("Occasion", occasion != null ? occasion : "Payment");
        return payload;
    }

    // Fields per developer.safaricom.co.ke/apis/BusinessPayBill and /apis/BusinessBuyGoods: both identifier
    // types "4" only; AccountReference up to 13 characters, Remarks up to 100; Requester is optional (unused).
    private Map<String, Object> buildB2BPaymentPayload(MpesaShortCode shortcode, B2BPaymentRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("Initiator", shortcode.getInitiatorName());
        payload.put("SecurityCredential", securityCredentialService.credentialFor(shortcode));
        payload.put("CommandID", request.commandId());
        payload.put("SenderIdentifierType", "4");
        payload.put("RecieverIdentifierType", "4");
        payload.put("Amount", request.amount().setScale(0, java.math.RoundingMode.UNNECESSARY).toPlainString());
        payload.put("PartyA", shortcode.getShortcode());
        payload.put("PartyB", request.receiverShortcode());
        if (request.accountReference() != null && !request.accountReference().isBlank()) {
            payload.put("AccountReference", request.accountReference());
        }
        payload.put("Remarks", request.remarks() != null && !request.remarks().isBlank() ? request.remarks() : "Sweep");
        payload.put("QueueTimeOutURL", callbackUrl("/b2b/timeout"));
        payload.put("ResultURL", callbackUrl("/b2b/result"));
        return payload;
    }

    private Map<String, Object> buildTransactionStatusPayload(MpesaShortCode shortcode,
                                                              String transactionId, String remarks) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("Initiator", shortcode.getInitiatorName());
        payload.put("SecurityCredential", securityCredentialService.credentialFor(shortcode));
        payload.put("CommandID", "TransactionStatusQuery");
        payload.put("TransactionID", transactionId);
        payload.put("PartyA", shortcode.getShortcode());
        // IdentifierType 2 is a till number, 4 an organisation shortcode (paybill).
        payload.put("IdentifierType", shortcode.getShortcodeType() == ShortcodeType.TILL ? "2" : "4");
        payload.put("ResultURL", callbackUrl("/transaction-status/result"));
        payload.put("QueueTimeOutURL", callbackUrl("/transaction-status/timeout"));
        payload.put("Remarks", remarks != null ? remarks : "Transaction Status Query");
        payload.put("Occasion", "TransactionStatusQuery");
        return payload;
    }

    // Fields per developer.safaricom.co.ke/apis/AccountBalance; IdentifierType 4 (organisation shortcode) is
    // the only value the page documents, so tills are not given a different type here.
    private Map<String, Object> buildAccountBalancePayload(MpesaShortCode shortcode, String remarks) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("Initiator", shortcode.getInitiatorName());
        payload.put("SecurityCredential", securityCredentialService.credentialFor(shortcode));
        payload.put("CommandID", "AccountBalance");
        payload.put("PartyA", shortcode.getShortcode());
        payload.put("IdentifierType", "4");
        payload.put("Remarks", remarks != null && !remarks.isBlank() ? remarks : "Account balance");
        payload.put("QueueTimeOutURL", callbackUrl("/account-balance/timeout"));
        payload.put("ResultURL", callbackUrl("/account-balance/result"));
        return payload;
    }

    private Map<String, Object> buildTransactionReversalPayload(MpesaShortCode shortcode, String transactionId,
                                                                BigDecimal amount, String remarks) {
        // Fields as on developer.safaricom.co.ke/apis/Reversal: ReceiverParty is the organisation shortcode,
        // RecieverIdentifierType (Safaricom's spelling) "11", Remarks 2-100 characters, no Occasion.
        Map<String, Object> payload = new HashMap<>();
        payload.put("Initiator", shortcode.getInitiatorName());
        payload.put("SecurityCredential", securityCredentialService.credentialFor(shortcode));
        payload.put("CommandID", "TransactionReversal");
        payload.put("TransactionID", transactionId);
        payload.put("Amount", amount.stripTrailingZeros().toPlainString());
        payload.put("ReceiverParty", shortcode.getShortcode());
        payload.put("RecieverIdentifierType", "11");
        payload.put("ResultURL", callbackUrl("/reversal/result"));
        payload.put("QueueTimeOutURL", callbackUrl("/reversal/timeout"));
        payload.put("Remarks", remarks != null && remarks.length() >= 2 ? remarks : "Transaction Reversal");
        return payload;
    }

    // Daraja Dynamic QR fields (MerchantName, RefNo, Amount numeric, TrxCode, CPI, Size string) per the portal
    // docs, mirrored at github.com/JacksCodeVault/mpesa-daraja-mcp daraja_docs_v3/docs/DynamicQRCode.md.
    // Amount is omitted when none is given (unverified whether Daraja then lets the customer type it).
    static Map<String, Object> buildQRCodePayload(MpesaShortCode shortcode, String merchantName,
                                                  String accountReference, BigDecimal amount,
                                                  QRTransactionType transactionType, Integer qrCodeSize) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("MerchantName", merchantName != null ? merchantName : shortcode.getBusinessName());
        payload.put("RefNo", accountReference != null ? accountReference : shortcode.getAccountReference());
        if (amount != null) {
            payload.put("Amount", amount.stripTrailingZeros().scale() <= 0 ? amount.toBigInteger() : amount);
        }
        payload.put("TrxCode", transactionType.getCode());
        payload.put("CPI", shortcode.getShortcode());
        payload.put("Size", qrCodeSize != null ? qrCodeSize.toString() : "300");
        return payload;
    }

}