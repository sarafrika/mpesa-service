package com.sarafrika.apps.mpesaservice.exceptions.handlers;

import com.sarafrika.apps.mpesaservice.controllers.MpesaPaymentController;
import com.sarafrika.apps.mpesaservice.controllers.MpesaPayoutController;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;
import com.sarafrika.apps.mpesaservice.exceptions.ProductAccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Grant denials (403) and type refusals (409) on the machine API, in the MpesaApiResponse envelope. */
@RestControllerAdvice(assignableTypes = {MpesaPaymentController.class, MpesaPayoutController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ProductGrantExceptionHandler {

    public static final String GRANT_REQUIRED = "GRANT_REQUIRED";

    @ExceptionHandler(ProductAccessDeniedException.class)
    public ResponseEntity<MpesaApiResponse<Void>> denied(ProductAccessDeniedException ex) {
        log.info("Machine API call refused: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(MpesaApiResponse.error(GRANT_REQUIRED, ex.getMessage(), HttpStatus.FORBIDDEN.value()));
    }

    /** The shortcode's type cannot do this (a till paying out): 409, and nothing reached Safaricom. */
    @ExceptionHandler(CapabilityNotSupportedException.class)
    public ResponseEntity<MpesaApiResponse<Void>> unsupported(CapabilityNotSupportedException ex) {
        log.info("Machine API call refused: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(MpesaApiResponse.error(
                CapabilityNotSupportedException.CODE, ex.getMessage(), HttpStatus.CONFLICT.value()));
    }
}
