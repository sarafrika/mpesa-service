package com.sarafrika.apps.mpesaservice.services.callbacks;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackAcks.Processing;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import org.junit.jupiter.api.Test;

/** Reading outcomes from acks, kinds from paths and ids from bodies. */
class CallbackAcksTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }

    @Test
    void zeroCodesAreSuccessAndOthersFailWithTheirDescription() throws Exception {
        assertThat(CallbackAcks.read(CallbackKind.B2C_RESULT, 200, json("{\"ResultCode\":\"00000000\"}"), null))
                .isEqualTo(Processing.success());
        assertThat(CallbackAcks.read(CallbackKind.C2B_CONFIRMATION, 200, json("{\"ResultCode\":\"0\"}"), null).ok())
                .isTrue();
        assertThat(CallbackAcks.read(CallbackKind.B2C_RESULT, 200,
                json("{\"ResultCode\":\"00000001\",\"ResultDesc\":\"Internal server error\"}"), null))
                .isEqualTo(Processing.failure("Acknowledged with ResultCode 00000001: Internal server error"));
        assertThat(CallbackAcks.read(CallbackKind.OTHER, 500, null, null).ok()).isFalse();
    }

    @Test
    void aCallbackRefusedBeforeAnyHandlerIsRejectedNotFailed() {
        for (int status : new int[] {401, 403, 404}) {
            Processing refused = CallbackAcks.read(CallbackKind.STK_CALLBACK, status, null, null);
            assertThat(refused.ok()).isFalse();
            assertThat(refused.rejected()).isTrue();
            assertThat(refused.error()).contains("HTTP " + status).contains("nothing was applied");
        }
        assertThat(CallbackAcks.read(CallbackKind.STK_CALLBACK, 500, null, null).rejected()).isFalse();
    }

    @Test
    void aValidationRejectionCodeIsADecisionOnlyForValidation() throws Exception {
        JsonNode reject = json("{\"ResultCode\":\"C2B00012\",\"ResultDesc\":\"Rejected\"}");
        assertThat(CallbackAcks.read(CallbackKind.C2B_VALIDATION, 200, reject, null).ok()).isTrue();
        assertThat(CallbackAcks.read(CallbackKind.C2B_CONFIRMATION, 200, reject, null).ok()).isFalse();
        assertThat(CallbackAcks.read(CallbackKind.C2B_VALIDATION, 200, reject, "boom").error()).isEqualTo("boom");
    }

    @Test
    void reportsOutsideACaptureAreIgnoredAndLongErrorsAreShortened() {
        CallbackOutcome.failed("nobody listens");
        CallbackOutcome.begin();
        CallbackOutcome.failed(new IllegalArgumentException("x".repeat(400) + "\n at stack"));
        CallbackOutcome.failed("second report loses");
        String reported = CallbackOutcome.end();
        assertThat(reported).startsWith("IllegalArgumentException: xxx").hasSize(CallbackOutcome.MAX_ERROR)
                .doesNotContain("\n");
        assertThat(CallbackOutcome.end()).isNull();
    }

    @Test
    void kindsComeFromThePath() {
        assertThat(CallbackKind.fromPath("/api/v1/callbacks/c2b/confirmation")).isEqualTo(CallbackKind.C2B_CONFIRMATION);
        assertThat(CallbackKind.fromPath("/api/v1/callbacks/transaction-status/result"))
                .isEqualTo(CallbackKind.TRANSACTION_STATUS_RESULT);
        assertThat(CallbackKind.fromPath("/api/v1/callbacks/stk/callback/")).isEqualTo(CallbackKind.STK_CALLBACK);
        assertThat(CallbackKind.fromPath("/api/v1/callbacks/unknown")).isEqualTo(CallbackKind.OTHER);
    }

    @Test
    void idsAreFoundAtAnyDepthIncludingNameValueItems() throws Exception {
        JsonNode stk = json("""
                {"Body":{"stkCallback":{"MerchantRequestID":"29115-1","CheckoutRequestID":"ws_CO_1","ResultCode":0,
                 "CallbackMetadata":{"Item":[{"Name":"Amount","Value":1},{"Name":"MpesaReceiptNumber","Value":"NLJ7RT61SV"},
                 {"Name":"PhoneNumber","Value":254708374149}]}}}}""");
        assertThat(CallbackReferences.ids(stk)).containsEntry("CheckoutRequestID", "ws_CO_1")
                .containsEntry("MpesaReceiptNumber", "NLJ7RT61SV").doesNotContainKey("PhoneNumber");
        assertThat(CallbackReferences.searchText(stk)).isEqualTo("29115-1 ws_CO_1 NLJ7RT61SV");
        assertThat(CallbackReferences.searchText(null)).isNull();
    }
}
