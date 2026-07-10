package com.sarafrika.apps.mpesaservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import com.sarafrika.apps.mpesaservice.repositories.MpesaIncomingPaymentRepository;
import com.sarafrika.apps.mpesaservice.services.impl.MpesaIncomingPaymentServiceImpl;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MpesaIncomingPaymentServiceImplTest {

    private MpesaIncomingPaymentRepository repository;
    private MpesaIncomingPaymentServiceImpl service;

    private static final String CHECKOUT_ID = "ws_CO_04112017184930742";

    @BeforeEach
    void setUp() {
        repository = mock(MpesaIncomingPaymentRepository.class);
        service = new MpesaIncomingPaymentServiceImpl(repository, new ObjectMapper());
        // save returns the passed entity
        when(repository.save(any(MpesaIncomingPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MpesaIncomingPayment pendingStkPayment() {
        MpesaIncomingPayment payment = new MpesaIncomingPayment();
        payment.setPaymentType(IncomingPaymentType.STK_PUSH);
        payment.setCheckoutRequestId(CHECKOUT_ID);
        payment.setTransactionId(CHECKOUT_ID); // placeholder seeded at initiation
        payment.setPhoneNumber("254708374149");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setStatus(IncomingPaymentStatus.PENDING);
        return payment;
    }

    private Map<String, Object> successCallback() {
        return Map.of(
                "MerchantRequestID", "21605-295434-4",
                "CheckoutRequestID", CHECKOUT_ID,
                "ResultCode", 0,
                "ResultDesc", "The service request is processed successfully.",
                "CallbackMetadata", Map.of("Item", List.of(
                        Map.of("Name", "Amount", "Value", 100.0),
                        Map.of("Name", "MpesaReceiptNumber", "Value", "QDR123ABCD"),
                        Map.of("Name", "PhoneNumber", "Value", "254708374149"),
                        Map.of("Name", "TransactionDate", "Value", "20230727104247")
                ))
        );
    }

    @Test
    void processStkPushCallback_success_transitionsToSuccessAndStoresReceipt() {
        when(repository.findByCheckoutRequestId(CHECKOUT_ID)).thenReturn(Optional.of(pendingStkPayment()));

        MpesaIncomingPayment result = service.processStkPushCallback(CHECKOUT_ID, successCallback());

        assertThat(result.getStatus()).isEqualTo(IncomingPaymentStatus.SUCCESS);
        assertThat(result.getResultCode()).isZero();
        assertThat(result.getTransactionId()).isEqualTo("QDR123ABCD");
        assertThat(result.getProcessedAt()).isNotNull();
        assertThat(result.getRawCallbackData()).isNotNull();
        assertThat(result.getTransactionDate()).isNotNull();
    }

    @Test
    void processStkPushCallback_userCancelled_transitionsToCancelled() {
        when(repository.findByCheckoutRequestId(CHECKOUT_ID)).thenReturn(Optional.of(pendingStkPayment()));

        Map<String, Object> cancelled = Map.of(
                "CheckoutRequestID", CHECKOUT_ID,
                "ResultCode", 1032,
                "ResultDesc", "Request cancelled by user");

        MpesaIncomingPayment result = service.processStkPushCallback(CHECKOUT_ID, cancelled);

        assertThat(result.getStatus()).isEqualTo(IncomingPaymentStatus.CANCELLED);
        assertThat(result.getResultCode()).isEqualTo(1032);
        // receipt stays as the placeholder since no successful metadata arrived
        assertThat(result.getTransactionId()).isEqualTo(CHECKOUT_ID);
    }

    @Test
    void processStkPushCallback_failure_transitionsToFailed() {
        when(repository.findByCheckoutRequestId(CHECKOUT_ID)).thenReturn(Optional.of(pendingStkPayment()));

        Map<String, Object> failed = Map.of(
                "CheckoutRequestID", CHECKOUT_ID,
                "ResultCode", 1,
                "ResultDesc", "The balance is insufficient for the transaction.");

        MpesaIncomingPayment result = service.processStkPushCallback(CHECKOUT_ID, failed);

        assertThat(result.getStatus()).isEqualTo(IncomingPaymentStatus.FAILED);
        assertThat(result.getResultCode()).isEqualTo(1);
    }

    @Test
    void processStkPushCallback_unknownCheckoutId_throws() {
        when(repository.findByCheckoutRequestId(CHECKOUT_ID)).thenReturn(Optional.empty());

        try {
            service.processStkPushCallback(CHECKOUT_ID, successCallback());
            assertThat(false).as("expected IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains(CHECKOUT_ID);
        }
    }
}
