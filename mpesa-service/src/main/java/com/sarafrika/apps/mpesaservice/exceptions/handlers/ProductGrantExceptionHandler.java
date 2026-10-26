package com.sarafrika.apps.mpesaservice.exceptions.handlers;

import com.sarafrika.apps.mpesaservice.controllers.MpesaPaymentController;
import com.sarafrika.apps.mpesaservice.controllers.MpesaPayoutController;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.exceptions.ProductAccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Grant denials on the machine API answer 403 in the MpesaApiResponse envelope those endpoints use. */
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
}
