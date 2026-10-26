package com.sarafrika.apps.mpesaservice.controllers;

import com.sarafrika.apps.mpesaservice.dtos.B2CPayoutRequest;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaPayoutDto;
import com.sarafrika.apps.mpesaservice.dtos.PayoutFloatDto;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.services.payout.MpesaPayoutService;
import com.sarafrika.apps.mpesaservice.services.products.CallerIdentity;
import com.sarafrika.apps.mpesaservice.services.products.ProductGrantGuard;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * B2C payouts for consuming systems. Each payout belongs to the Keycloak client that asked for it,
 * which alone can read it back; its client_reference makes a retry safe.
 */
@RestController
@RequestMapping("/api/v1/mpesa")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "M-Pesa Payouts", description = "Send B2C payouts and poll their outcome")
public class MpesaPayoutController {

    private final MpesaPayoutService payoutService;
    private final ProductGrantGuard grantGuard;

    @PostMapping("/b2c")
    @Operation(operationId = "sendB2cPayout", summary = "Send a B2C payout",
            description = "202 while Safaricom processes it; the same client_reference returns the first payout.")
    @ApiResponse(responseCode = "403", description = "The calling client is not granted B2C_PAYOUT on the shortcode",
            content = @Content(schema = @Schema(implementation = MpesaApiResponse.class)))
    public ResponseEntity<MpesaApiResponse<MpesaPayoutDto>> payout(@Valid @RequestBody B2CPayoutRequest request,
                                                                   Authentication authentication) {
        grantGuard.require(authentication, request.shortcodeUuid(), GrantOperation.B2C_PAYOUT);
        try {
            MpesaOutgoingPayment payment = payoutService.initiate(clientId(authentication), request);
            HttpStatus status = payment.getStatus() == OutgoingPaymentStatus.FAILED ? HttpStatus.BAD_GATEWAY
                    : payment.getStatus() == OutgoingPaymentStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK;
            return ResponseEntity.status(status)
                    .body(MpesaApiResponse.success(MpesaPayoutDto.from(payment), status.value()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(MpesaApiResponse.error("INVALID_REQUEST", ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
        } catch (IllegalStateException ex) {
            log.error("Payout not possible: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(MpesaApiResponse.error(
                    "PAYOUT_NOT_CONFIGURED", ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE.value()));
        }
    }

    @GetMapping("/payouts/{uuid}")
    @Operation(operationId = "getPayout", summary = "Fetch one of your payouts")
    public ResponseEntity<MpesaPayoutDto> get(@PathVariable UUID uuid, Authentication authentication) {
        return payoutService.findForClient(clientId(authentication), uuid)
                .map(MpesaPayoutDto::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/payouts")
    @Operation(operationId = "getPayoutByClientReference",
            summary = "Fetch one of your payouts by your own reference")
    public ResponseEntity<MpesaPayoutDto> getByReference(@RequestParam("client_reference") String clientReference,
                                                         Authentication authentication) {
        return payoutService.findForClient(clientId(authentication), clientReference)
                .map(MpesaPayoutDto::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/shortcodes/{uuid}/float")
    @Operation(operationId = "getShortcodeFloat",
            summary = "A shortcode's float as last reported on a payout result")
    @ApiResponse(responseCode = "403", description = "A product without B2C_PAYOUT or STATUS_LOOKUP on the shortcode",
            content = @Content(schema = @Schema(implementation = MpesaApiResponse.class)))
    public ResponseEntity<PayoutFloatDto> payoutFloat(@PathVariable UUID uuid, Authentication authentication) {
        grantGuard.requireRead(authentication, uuid, GrantOperation.B2C_PAYOUT, GrantOperation.STATUS_LOOKUP);
        return ResponseEntity.ok(payoutService.latestFloat(uuid));
    }

    /** The calling Keycloak client (its azp), so one system never sees another's payouts. */
    static String clientId(Authentication authentication) {
        return CallerIdentity.clientId(authentication);
    }
}
